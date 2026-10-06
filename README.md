# GitHub Stats Discord Bot (Java)

## What

A Discord bot that looks up GitHub accounts via the GitHub API and posts the
info as an embed: account creation date, last public activity, followers,
and (with a GitHub token) total stars/commits/PRs/issues in the last year.

## Why

Built as a personal project to make GitHub stats checkable right inside a
Discord server, without anyone having to open a browser.

## When

Built in September 2026.

## What we used

![Java](https://skillicons.dev/icons?i=java) ![Maven](https://skillicons.dev/icons?i=maven) ![GitHub](https://skillicons.dev/icons?i=github)

- **Language:** Java 17
- **Build:** Maven (single fat jar via the maven-shade-plugin, `Main-Class` set to `com.example.githubbot.Main`)
- **Discord API:** JDA 5 (Java Discord API, slash commands + embeds)
- **HTTP client:** OkHttp 4 for the GitHub REST and GraphQL APIs
- **JSON parsing:** Gson
- **Logging:** SLF4J with the slf4j-simple backend
- **Config:** `.env` file auto-loaded at startup (real environment variables take priority)

## Why we used this

- **Java 17:** the language the bot is written in, with current syntax for the command handlers and data models.
- **JDA 5:** the standard Java wrapper for the Discord API; it handles slash command registration and rich embeds.
- **OkHttp:** performs the bot's calls to the GitHub REST API (profiles, repos, languages) and the GitHub GraphQL API (contribution stats).
- **Gson:** parses the JSON responses from GitHub into the objects the embeds are built from.
- **Maven shade plugin:** bundles everything into one fat jar, so the bot runs anywhere Java is installed with a single `java -jar` command.
- **SLF4J-simple:** lightweight logging at startup and on API failures.

## How it works

- A user runs a slash command in Discord, e.g. `/github profile username:octocat`.
- `GitHubCommandListener` receives the command event from JDA.
- `GitHubService` queries the GitHub REST API (profiles, repos, languages) or the GraphQL API (contribution counts) using OkHttp, with the optional `GITHUB_TOKEN` attached for authenticated rate limits.
- The listener formats the API response into a Discord embed (stats tables, text progress bars for languages) and replies in the channel.

## Commands

- `/github profile username:<name>`: account created date, last public
  activity, followers, following, public repo count. Works with or without
  a GitHub token (unauthenticated GitHub API calls are capped at 60/hour).
- `/github stats username:<name>`: total stars across owned repos, commits,
  PRs, issues, and repos contributed to over the last year. **Requires a
  `GITHUB_TOKEN`** because GitHub only exposes contribution stats through the
  authenticated GraphQL API (this is the same data source tools like
  github-readme-stats use).
- `/github repo owner:<owner> name:<repo>`: one repo's stars, forks,
  watchers, open issues+PRs, primary language, license, and last push time.
- `/langs username:<name>`: percentage breakdown of every language used
  across the user's owned, non-fork public repos, with a text progress bar
  per language (mirrors the "Most Used Languages" card GitHub stats widgets
  show). Fetches each repo's language stats concurrently to stay fast.

## Getting started

### 1. Create the Discord bot

1. Go to https://discord.com/developers/applications → **New Application**.
2. Go to the **Bot** tab → **Reset Token** → copy it. This is your `DISCORD_TOKEN`.
3. Still on the Bot tab, no privileged intents are required for this bot.
4. Go to **OAuth2 → URL Generator**, check scopes `bot` and
   `applications.commands`, and under Bot Permissions check `Send Messages`
   and `Embed Links`. Open the generated URL to invite the bot to your server.

### 2. Create a GitHub token (optional but recommended)

1. https://github.com/settings/tokens → **Generate new token (classic)**.
2. No special scopes are needed for public data: an unscoped token is fine
   (it just needs to be an authenticated request). Copy it as `GITHUB_TOKEN`.
3. Without this token, `/github stats` won't work, and `/github profile` will
   be limited to 60 requests/hour instead of 5,000/hour.

### 3. Configure and run

Copy `.env.example` to `.env` and fill in your real tokens:

```bash
cp .env.example .env
```

```
DISCORD_TOKEN=your_discord_bot_token_here
GITHUB_TOKEN=your_github_personal_access_token_here
```

`.env` is already listed in `.gitignore`, so it will never be committed if you
push this project to GitHub. The bot reads it automatically at startup: you
don't need to `export` anything manually (though real environment variables,
e.g. from Docker or a hosting provider, still work and take priority over `.env`).

Then build and run with Maven:

```bash
mvn clean package
java -jar target/github-discord-bot.jar
```

On first run, global slash commands can take up to an hour to show up in
Discord. For instant updates while developing, edit `Main.java` and register
commands to a specific guild instead of globally:

```java
jda.getGuildById(YOUR_GUILD_ID).updateCommands().addCommands(...).queue();
```

## Project layout

```
github-discord-bot/
├── pom.xml                                          # Maven build (JDA, OkHttp, Gson, shade plugin)
└── src/main/java/com/example/githubbot/
    ├── Main.java                    # Bootstraps JDA, registers slash commands
    ├── GitHubService.java           # REST + GraphQL calls to api.github.com
    └── GitHubCommandListener.java   # Handles /github, builds the embeds
```

## Notes / things you can extend

- `/github stats` counts stars only on *owned* public repos and only the
  first 100: paginate `repositories(first: 100, after: $cursor)` if someone
  has more than 100 repos.
- Contribution counts (commits/PRs/issues) reflect the **last 12 months**,
  same window as GitHub's contribution graph: pass a `from`/`to` range into
  `contributionsCollection` if you want a different period or all-time totals
  (all-time commit counts aren't available via a single API call; you'd need
  to sum per-repo commit stats, which is slow and rate-limit heavy).

## Credits

**Namish Yadav**
- GitHub: [https://github.com/p3xz](https://github.com/p3xz)
- LinkedIn: [https://www.linkedin.com/in/namish-yadav-639769408/](https://www.linkedin.com/in/namish-yadav-639769408/)
- Instagram: [https://instagram.com/nam7sh](https://instagram.com/nam7sh)
