package com.example.githubbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

public class GitHubCommandListener extends ListenerAdapter {

    private final GitHubService gitHub;

    public GitHubCommandListener(GitHubService gitHub) {
        this.gitHub = gitHub;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        if (!command.equals("github") && !command.equals("langs")) return;

        String sub = event.getSubcommandName();

        // GitHub API calls are blocking (OkHttp synchronous), so defer the reply
        // and do the work off the gateway thread.
        event.deferReply().queue();

        new Thread(() -> {
            try {
                if (command.equals("langs")) {
                    handleLangs(event, event.getOption("username").getAsString().trim());
                } else if ("profile".equals(sub)) {
                    handleProfile(event, event.getOption("username").getAsString().trim());
                } else if ("stats".equals(sub)) {
                    handleStats(event, event.getOption("username").getAsString().trim());
                } else if ("repo".equals(sub)) {
                    handleRepo(event,
                            event.getOption("owner").getAsString().trim(),
                            event.getOption("name").getAsString().trim());
                }
            } catch (GitHubService.GitHubApiException e) {
                event.getHook().sendMessage("❌ " + e.getMessage()).queue();
            } catch (Exception e) {
                event.getHook().sendMessage("❌ Something went wrong talking to the GitHub API: " + e.getMessage()).queue();
            }
        }).start();
    }

    private void handleProfile(SlashCommandInteractionEvent event, String username) throws Exception {
        JsonObject user = gitHub.fetchUserProfile(username);
        JsonArray events = gitHub.fetchRecentPublicEvents(username);

        String login = user.get("login").getAsString();
        String avatarUrl = user.get("avatar_url").getAsString();
        String htmlUrl = user.get("html_url").getAsString();
        String name = user.has("name") && !user.get("name").isJsonNull() ? user.get("name").getAsString() : login;
        String bio = user.has("bio") && !user.get("bio").isJsonNull() ? user.get("bio").getAsString() : null;
        int followers = user.get("followers").getAsInt();
        int following = user.get("following").getAsInt();
        int publicRepos = user.get("public_repos").getAsInt();
        String createdAtRaw = user.get("created_at").getAsString();
        String updatedAtRaw = user.get("updated_at").getAsString();

        OffsetDateTime createdAt = OffsetDateTime.parse(createdAtRaw);
        long accountAgeDays = ChronoUnit.DAYS.between(createdAt, OffsetDateTime.now());
        long years = accountAgeDays / 365;
        long remDays = accountAgeDays % 365;

        String lastActivity;
        if (events.size() > 0) {
            JsonObject latest = events.get(0).getAsJsonObject();
            String type = latest.get("type").getAsString();
            String createdRaw = latest.get("created_at").getAsString();
            OffsetDateTime eventTime = OffsetDateTime.parse(createdRaw);
            lastActivity = humanizeEventType(type) + " — " + relativeTime(eventTime) + " (<t:" + eventTime.toEpochSecond() + ":f>)";
        } else {
            lastActivity = "No recent public activity found (account may have no public events, or activity is private).";
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(name + " (@" + login + ")", htmlUrl);
        embed.setThumbnail(avatarUrl);
        embed.setColor(new Color(240, 140, 160));
        if (bio != null) embed.setDescription(bio);

        embed.addField("Account created", "<t:" + createdAt.toEpochSecond() + ":D> (" + years + "y " + remDays + "d ago)", false);
        embed.addField("Last public activity", lastActivity, false);
        embed.addField("Followers", String.valueOf(followers), true);
        embed.addField("Following", String.valueOf(following), true);
        embed.addField("Public repos", String.valueOf(publicRepos), true);
        embed.setFooter("Profile last updated " + relativeTime(OffsetDateTime.parse(updatedAtRaw)));

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleStats(SlashCommandInteractionEvent event, String username) throws Exception {
        JsonObject user = gitHub.fetchContributionStats(username);

        JsonObject repos = user.getAsJsonObject("repositories");
        int totalRepoCount = repos.get("totalCount").getAsInt();
        int totalStars = 0;
        for (var el : repos.getAsJsonArray("nodes")) {
            totalStars += el.getAsJsonObject().get("stargazerCount").getAsInt();
        }

        JsonObject contrib = user.getAsJsonObject("contributionsCollection");
        int totalCommits = contrib.get("totalCommitContributions").getAsInt();
        int totalPRs = contrib.get("totalPullRequestContributions").getAsInt();
        int totalIssues = contrib.get("totalIssueContributions").getAsInt();
        int contributedTo = contrib.get("totalRepositoriesWithContributedCommits").getAsInt();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(username + "'s GitHub Stats (last year)", "https://github.com/" + username);
        embed.setColor(new Color(240, 140, 160));
        embed.addField("⭐ Total stars (owned repos)", String.valueOf(totalStars), true);
        embed.addField("📦 Public owned repos", String.valueOf(totalRepoCount), true);
        embed.addField("✅ Commits (last yr)", String.valueOf(totalCommits), true);
        embed.addField("🔀 Pull requests (last yr)", String.valueOf(totalPRs), true);
        embed.addField("🐛 Issues (last yr)", String.valueOf(totalIssues), true);
        embed.addField("🤝 Repos contributed to", String.valueOf(contributedTo), true);
        embed.setFooter("Note: commit/PR/issue counts cover the last 12 months, matching GitHub's own contribution graph window.");

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleRepo(SlashCommandInteractionEvent event, String owner, String repoName) throws Exception {
        JsonObject repo = gitHub.fetchRepo(owner, repoName);

        String fullName = repo.get("full_name").getAsString();
        String htmlUrl = repo.get("html_url").getAsString();
        String description = repo.has("description") && !repo.get("description").isJsonNull()
                ? repo.get("description").getAsString() : null;
        int stars = repo.get("stargazers_count").getAsInt();
        int forks = repo.get("forks_count").getAsInt();
        int watchers = repo.get("subscribers_count").getAsInt();
        int openIssuesAndPrs = repo.get("open_issues_count").getAsInt(); // GitHub bundles PRs into this count
        String language = repo.has("language") && !repo.get("language").isJsonNull()
                ? repo.get("language").getAsString() : "Not detected";
        String license = repo.has("license") && !repo.get("license").isJsonNull()
                ? repo.getAsJsonObject("license").get("name").getAsString() : "None";
        boolean archived = repo.get("archived").getAsBoolean();
        OffsetDateTime createdAt = OffsetDateTime.parse(repo.get("created_at").getAsString());
        OffsetDateTime pushedAt = OffsetDateTime.parse(repo.get("pushed_at").getAsString());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(fullName + (archived ? " (archived)" : ""), htmlUrl);
        embed.setColor(new Color(240, 140, 160));
        if (description != null) embed.setDescription(description);

        embed.addField("⭐ Stars", String.valueOf(stars), true);
        embed.addField("🍴 Forks", String.valueOf(forks), true);
        embed.addField("👀 Watchers", String.valueOf(watchers), true);
        embed.addField("🐛 Open issues + PRs", String.valueOf(openIssuesAndPrs), true);
        embed.addField("💻 Primary language", language, true);
        embed.addField("📄 License", license, true);
        embed.addField("Created", "<t:" + createdAt.toEpochSecond() + ":D>", true);
        embed.addField("Last pushed", "<t:" + pushedAt.toEpochSecond() + ":R>", true);

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    private void handleLangs(SlashCommandInteractionEvent event, String username) throws Exception {
        Map<String, Long> breakdown = gitHub.fetchLanguageBreakdown(username);

        if (breakdown.isEmpty()) {
            event.getHook().sendMessage("No language data found — the account may have no public, non-fork repos.").queue();
            return;
        }

        long totalBytes = breakdown.values().stream().mapToLong(Long::longValue).sum();

        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (var entry : breakdown.entrySet()) {
            if (shown >= 12) break; // keep the embed readable
            double pct = (entry.getValue() * 100.0) / totalBytes;
            if (pct < 0.1) break; // stop once entries round to 0.0%
            sb.append(String.format("`%-16s` %s %5.2f%%%n", entry.getKey(), progressBar(pct), pct));
            shown++;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(username + "'s Most Used Languages", "https://github.com/" + username);
        embed.setColor(new Color(240, 140, 160));
        embed.setDescription(sb.toString());
        embed.setFooter("Based on bytes of code across all owned, non-fork public repos.");

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }

    /** Builds a 10-cell text progress bar, e.g. "██████░░░░" for 64%. */
    private String progressBar(double percent) {
        int filled = (int) Math.round(percent / 10.0);
        filled = Math.max(0, Math.min(10, filled));
        return "█".repeat(filled) + "░".repeat(10 - filled);
    }

    private String humanizeEventType(String type) {
        return switch (type) {
            case "PushEvent" -> "Pushed commits";
            case "PullRequestEvent" -> "Opened/updated a pull request";
            case "IssuesEvent" -> "Opened/updated an issue";
            case "CreateEvent" -> "Created a repo/branch/tag";
            case "ForkEvent" -> "Forked a repo";
            case "WatchEvent" -> "Starred a repo";
            case "IssueCommentEvent" -> "Commented on an issue/PR";
            case "PullRequestReviewEvent" -> "Reviewed a pull request";
            default -> type;
        };
    }

    private String relativeTime(OffsetDateTime time) {
        Duration d = Duration.between(time, Instant.now().atOffset(time.getOffset()));
        long days = d.toDays();
        if (days > 0) return days + "d ago";
        long hours = d.toHours();
        if (hours > 0) return hours + "h ago";
        long minutes = d.toMinutes();
        if (minutes > 0) return minutes + "m ago";
        return "just now";
    }
}
