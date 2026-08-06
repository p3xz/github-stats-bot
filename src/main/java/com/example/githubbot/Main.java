package com.example.githubbot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws Exception {
        // Load values from a .env file (if present) without overriding real
        // environment variables that are already set (e.g. in Docker/hosting configs).
        Map<String, String> dotenv = loadDotenv(Path.of(".env"));

        String discordToken = firstNonBlank(System.getenv("DISCORD_TOKEN"), dotenv.get("DISCORD_TOKEN"));
        String githubToken = firstNonBlank(System.getenv("GITHUB_TOKEN"), dotenv.get("GITHUB_TOKEN")); // optional, but recommended

        if (discordToken == null || discordToken.isBlank()) {
            System.err.println("ERROR: DISCORD_TOKEN environment variable is not set.");
            System.err.println("Set it to your bot's token from https://discord.com/developers/applications");
            System.exit(1);
        }

        if (githubToken == null || githubToken.isBlank()) {
            System.out.println("WARNING: GITHUB_TOKEN is not set. The bot will still work for basic profile");
            System.out.println("lookups, but GitHub's unauthenticated API rate limit is only 60 requests/hour");
            System.out.println("and detailed stats (commits/PRs/issues) require a token with a GraphQL query.");
        }

        GitHubService gitHubService = new GitHubService(githubToken);

        JDA jda = JDABuilder.createDefault(discordToken)
                .enableIntents(GatewayIntent.GUILD_MESSAGES) // not strictly needed for slash commands, kept minimal
                .addEventListeners(new GitHubCommandListener(gitHubService))
                .build();

        jda.awaitReady();

        // Register the slash commands (global — can take up to ~1 hour to propagate on first deploy;
        // use jda.getGuildById(id).updateCommands() instead during development for instant updates)
        jda.updateCommands().addCommands(
                Commands.slash("github", "Look up a GitHub account or repo")
                        .addSubcommands(
                                new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("profile",
                                        "Basic profile info: account age, last activity, followers, repos")
                                        .addOptions(new OptionData(OptionType.STRING, "username", "GitHub username", true)),
                                new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("stats",
                                        "Detailed stats: total stars, commits, PRs, issues (needs GITHUB_TOKEN)")
                                        .addOptions(new OptionData(OptionType.STRING, "username", "GitHub username", true)),
                                new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("repo",
                                        "Look up a single repo: stars, forks, issues, license, etc")
                                        .addOptions(
                                                new OptionData(OptionType.STRING, "owner", "Repo owner (user or org)", true),
                                                new OptionData(OptionType.STRING, "name", "Repo name", true))
                        ),
                Commands.slash("langs", "Language breakdown across a user's public repos")
                        .addOptions(new OptionData(OptionType.STRING, "username", "GitHub username", true))
        ).queue();

        System.out.println("Bot is up. Invite it with the applications.commands + bot scopes, then use /github.");
    }

    /**
     * Minimal .env parser: reads KEY=VALUE lines (ignores blank lines and lines
     * starting with #). No external dependency needed for something this small.
     * If the file doesn't exist, returns an empty map — that's fine, it just
     * means the bot relies on real environment variables instead.
     */
    private static Map<String, String> loadDotenv(Path path) {
        Map<String, String> values = new HashMap<>();
        if (!Files.exists(path)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                // Strip surrounding quotes if present, e.g. KEY="value"
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
                        || value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                values.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("Warning: failed to read .env file: " + e.getMessage());
        }
        return values;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
