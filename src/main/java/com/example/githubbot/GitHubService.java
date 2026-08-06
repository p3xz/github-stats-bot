package com.example.githubbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the GitHub REST and GraphQL APIs.
 *
 * REST is used for the "profile" command (account creation date, last public
 * event, followers, public repo count, etc) — this works with or without a token.
 *
 * GraphQL is used for the "stats" command (total stars across all owned repos,
 * total commits/PRs/issues in the last year, etc, similar to github-readme-stats).
 * This REQUIRES a token because contributionsCollection is only exposed via GraphQL.
 */
public class GitHubService {

    private static final String REST_BASE = "https://api.github.com";
    private static final String GRAPHQL_URL = "https://api.github.com/graphql";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String token; // may be null

    public GitHubService(String token) {
        this.token = token;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    // ---------- REST: basic profile ----------

    /** GET /users/{username} */
    public JsonObject fetchUserProfile(String username) throws IOException, GitHubApiException {
        Request.Builder builder = new Request.Builder()
                .url(REST_BASE + "/users/" + username)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.code() == 404) {
                throw new GitHubApiException("No GitHub user found with that username.");
            }
            if (!response.isSuccessful()) {
                throw new GitHubApiException("GitHub API error (" + response.code() + "): " + body);
            }
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    /** GET /users/{username}/events/public — used to find the most recent public activity */
    public JsonArray fetchRecentPublicEvents(String username) throws IOException, GitHubApiException {
        Request.Builder builder = new Request.Builder()
                .url(REST_BASE + "/users/" + username + "/events/public?per_page=5")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                // Not fatal — profile command can still show everything else without this
                return new JsonArray();
            }
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : new JsonArray();
        }
    }

    // ---------- REST: single repo lookup ----------

    /** GET /repos/{owner}/{repo} */
    public JsonObject fetchRepo(String owner, String repo) throws IOException, GitHubApiException {
        Request.Builder builder = new Request.Builder()
                .url(REST_BASE + "/repos/" + owner + "/" + repo)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.code() == 404) {
                throw new GitHubApiException("No repo found at `" + owner + "/" + repo + "` (it may be private or misspelled).");
            }
            if (!response.isSuccessful()) {
                throw new GitHubApiException("GitHub API error (" + response.code() + "): " + body);
            }
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    // ---------- REST: language breakdown across a user's repos ----------

    /** GET /users/{username}/repos — all public repos the user owns (non-forks), up to 100. */
    public JsonArray fetchOwnedNonForkRepos(String username) throws IOException, GitHubApiException {
        Request.Builder builder = new Request.Builder()
                .url(REST_BASE + "/users/" + username + "/repos?type=owner&per_page=100&sort=updated")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (response.code() == 404) {
                throw new GitHubApiException("No GitHub user found with that username.");
            }
            if (!response.isSuccessful()) {
                throw new GitHubApiException("GitHub API error (" + response.code() + "): " + body);
            }
            JsonArray all = JsonParser.parseString(body).getAsJsonArray();
            JsonArray ownedNonFork = new JsonArray();
            for (var el : all) {
                JsonObject r = el.getAsJsonObject();
                boolean isFork = r.has("fork") && r.get("fork").getAsBoolean();
                if (!isFork) ownedNonFork.add(r);
            }
            return ownedNonFork;
        }
    }

    /** GET /repos/{owner}/{repo}/languages — bytes of code per language for one repo. */
    public JsonObject fetchRepoLanguages(String owner, String repo) throws IOException, GitHubApiException {
        Request.Builder builder = new Request.Builder()
                .url(REST_BASE + "/repos/" + owner + "/" + repo + "/languages")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        try (Response response = client.newCall(builder.build()).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                return new JsonObject(); // skip repos that error out rather than failing the whole command
            }
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    /**
     * Aggregates language byte counts across every owned, non-fork public repo for a user.
     * Fetches each repo's /languages endpoint concurrently (bounded pool) since a user can
     * easily have 30-100 repos and doing this sequentially would be slow.
     *
     * Returns a map of language name -> total bytes, sorted descending by bytes.
     */
    public Map<String, Long> fetchLanguageBreakdown(String username) throws IOException, GitHubApiException {
        JsonArray repos = fetchOwnedNonForkRepos(username);

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(10, Math.max(1, repos.size())));
        try {
            List<Future<JsonObject>> futures = new java.util.ArrayList<>();
            for (var el : repos) {
                String repoName = el.getAsJsonObject().get("name").getAsString();
                Callable<JsonObject> task = () -> fetchRepoLanguages(username, repoName);
                futures.add(pool.submit(task));
            }

            Map<String, Long> totals = new java.util.HashMap<>();
            for (Future<JsonObject> future : futures) {
                JsonObject langs;
                try {
                    langs = future.get();
                } catch (ExecutionException | InterruptedException e) {
                    continue; // skip repos that failed to fetch
                }
                for (String lang : langs.keySet()) {
                    long bytes = langs.get(lang).getAsLong();
                    totals.merge(lang, bytes, Long::sum);
                }
            }

            // Sort descending by byte count
            Map<String, Long> sorted = new TreeMap<>((a, b) -> totals.get(b).compareTo(totals.get(a)) != 0
                    ? totals.get(b).compareTo(totals.get(a))
                    : a.compareTo(b));
            sorted.putAll(totals);
            return sorted;
        } finally {
            pool.shutdown();
        }
    }

    // ---------- GraphQL: detailed stats ----------

    /**
     * Fetches total stars (summed across owned repos), total commit/PR/issue
     * contributions in the last year, and how many repos the user contributed to.
     * Requires a token (classic PAT with at least "public_repo" / "read:user" scopes,
     * or a fine-grained token with read access to public repositories).
     */
    public JsonObject fetchContributionStats(String username) throws IOException, GitHubApiException {
        if (token == null || token.isBlank()) {
            throw new GitHubApiException("This command needs a GITHUB_TOKEN set on the bot (GraphQL API only " +
                    "exposes contribution stats to authenticated requests).");
        }

        String query = """
            query($login: String!) {
              user(login: $login) {
                repositories(first: 100, ownerAffiliations: OWNER, isFork: false, privacy: PUBLIC) {
                  totalCount
                  nodes {
                    stargazerCount
                  }
                }
                contributionsCollection {
                  totalCommitContributions
                  totalPullRequestContributions
                  totalIssueContributions
                  totalRepositoriesWithContributedCommits
                }
              }
            }
            """;

        JsonObject variables = new JsonObject();
        variables.addProperty("login", username);

        JsonObject payload = new JsonObject();
        payload.addProperty("query", query);
        payload.add("variables", variables);

        RequestBody body = RequestBody.create(payload.toString(), JSON);
        Request request = new Request.Builder()
                .url(GRAPHQL_URL)
                .header("Authorization", "Bearer " + token)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new GitHubApiException("GitHub GraphQL error (" + response.code() + "): " + responseBody);
            }
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("errors")) {
                throw new GitHubApiException("GitHub GraphQL error: " + json.get("errors").toString());
            }
            JsonObject user = json.getAsJsonObject("data").get("user").isJsonNull()
                    ? null
                    : json.getAsJsonObject("data").getAsJsonObject("user");
            if (user == null) {
                throw new GitHubApiException("No GitHub user found with that username.");
            }
            return user;
        }
    }

    /** Simple checked exception carrying a user-friendly message. */
    public static class GitHubApiException extends Exception {
        public GitHubApiException(String message) {
            super(message);
        }
    }
}
