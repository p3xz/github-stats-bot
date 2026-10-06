# GitHub Stats Bot

> A Discord bot that looks up GitHub accounts via the GitHub API and posts their stats as an embed, so you can check GitHub stats right inside a Discord server without opening a browser.

![Status](https://img.shields.io/badge/status-active-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

Built in September 2026.

## Features

- **Profile lookups**: `/github profile username:<name>` shows account creation date, last public activity, followers, following, and public repo count. Works with or without a GitHub token.
- **Yearly contribution stats**: `/github stats username:<name>` shows total stars across owned repos, commits, PRs, issues, and repos contributed to over the last year. Requires a `GITHUB_TOKEN`, because GitHub only exposes contribution stats through the authenticated GraphQL API (the same data source tools like github-readme-stats use).
- **Repo snapshots**: `/github repo owner:<owner> name:<repo>` shows one repo's stars, forks, watchers, open issues and PRs, primary language, license, and last push time.
- **Language breakdowns**: `/langs username:<name>` shows a percentage breakdown of every language used across the user's owned, non-fork public repos, with a text progress bar per language. Fetches each repo's language stats concurrently to stay fast.

## How it works

- A user runs a slash command in Discord, e.g. `/github profile username:octocat`.
- `GitHubCommandListener` receives the command event from JDA.
- `GitHubService` queries the GitHub REST API (profiles, repos, languages) or the GraphQL API (contribution counts) using OkHttp, with the optional `GITHUB_TOKEN` attached for authenticated rate limits.
- The listener formats the API response into a Discord embed (stats tables, text progress bars for languages) and replies in the channel.

## Tech Stack

![Java](https://skillicons.dev/icons?i=java) ![Maven](https://skillicons.dev/icons?i=maven) ![GitHub](https://skillicons.dev/icons?i=github)

- **Language:** Java 17
- **Build:** Maven (single fat jar via the maven-shade-plugin, `Main-Class` set to `com.example.githubbot.Main`)
- **Discord API:** JDA 5 (Java Discord API, slash commands + embeds)
- **HTTP client:** OkHttp 4 for the GitHub REST and GraphQL APIs
- **JSON parsing:** Gson
- **Logging:** SLF4J with the slf4j-simple backend
- **Config:** `.env` file auto-loaded at startup (real environment variables take priority)

Why these choices:

- **Java 17:** the language the bot is written in, with current syntax for the command handlers and data models.
- **JDA 5:** the standard Java wrapper for the Discord API; it handles slash command registration and rich embeds.
- **OkHttp:** performs the bot's calls to the GitHub REST API (profiles, repos, languages) and the GitHub GraphQL API (contribution stats).
- **Gson:** parses the JSON responses from GitHub into the objects the embeds are built from.
- **Maven shade plugin:** bundles everything into one fat jar, so the bot runs anywhere Java is installed with a single `java -jar` command.
- **SLF4J-simple:** lightweight logging at startup and on API failures.

## Quick Start

### Prerequisites

- Java 17 (set as `maven.compiler.source`/`target` in `pom.xml`)
- Maven (to run `mvn clean package`)
- A Discord server where you can add a bot

### Installation

1. Clone the repo:

```bash
git clone https://github.com/p3xz/github-stats-bot.git
cd github-stats-bot
```

2. Create the Discord bot:

   1. Go to https://discord.com/developers/applications and create a **New Application**.
   2. Go to the **Bot** tab, click **Reset Token**, and copy it. This is your `DISCORD_TOKEN`.
   3. Still on the Bot tab: no privileged intents are required for this bot.
   4. Go to **OAuth2 -> URL Generator**, check scopes `bot` and `applications.commands`, and under Bot Permissions check `Send Messages` and `Embed Links`. Open the generated URL to invite the bot to your server.

3. Create a GitHub token (optional but recommended):

   1. Go to https://github.com/settings/tokens and **Generate new token (classic)**.
   2. No special scopes are needed for public data: an unscoped token is fine (it just needs to be an authenticated request). Copy it as `GITHUB_TOKEN`.
   3. Without this token, `/github stats` won't work, and `/github profile` will be limited to 60 requests/hour instead of 5,000/hour.

4. Copy `.env.example` to `.env` and fill in your real tokens:

```bash
cp .env.example .env
```

`.env` is already listed in `.gitignore`, so it will never be committed if you push this project to GitHub. The bot reads it automatically at startup: you don't need to `export` anything manually (though real environment variables, e.g. from Docker or a hosting provider, still work and take priority over `.env`).

5. Build and run with Maven:

```bash
mvn clean package
java -jar target/github-discord-bot.jar
```

On first run, global slash commands can take up to an hour to show up in Discord. For instant updates while developing, edit `Main.java` and register commands to a specific guild instead of globally:

```java
jda.getGuildById(YOUR_GUILD_ID).updateCommands().addCommands(...).queue();
```

## Usage

In any Discord channel the bot is in, run:

```
/github profile username:octocat
```

The bot replies with an embed showing the account's creation date, last public activity, followers, following, and public repo count. See Features above for the other commands (`/github stats`, `/github repo`, `/langs`).

## Configuration

| Variable | Description | Default | Required |
|---|---|---|---|
| `DISCORD_TOKEN` | Discord bot token from the Bot tab of your Discord application | placeholder in `.env.example` | Yes |
| `GITHUB_TOKEN` | GitHub personal access token; raises the rate limit from 60 to 5,000 requests/hour and enables `/github stats` | placeholder in `.env.example` | No |

## Project layout

```
github-discord-bot/
├── pom.xml                                          # Maven build (JDA, OkHttp, Gson, shade plugin)
└── src/main/java/com/example/githubbot/
    ├── Main.java                    # Bootstraps JDA, registers slash commands
    ├── GitHubService.java           # REST + GraphQL calls to api.github.com
    └── GitHubCommandListener.java   # Handles /github, builds the embeds
```

## Notes

- `/github stats` counts stars only on *owned* public repos and only the first 100: paginate `repositories(first: 100, after: $cursor)` if someone has more than 100 repos.
- Contribution counts (commits/PRs/issues) reflect the **last 12 months**, same window as GitHub's contribution graph: pass a `from`/`to` range into `contributionsCollection` if you want a different period or all-time totals (all-time commit counts aren't available via a single API call; you'd need to sum per-repo commit stats, which is slow and rate-limit heavy).

## Contributing

Pull requests are welcome. Open an issue first to discuss what you would like to change.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Credits

**Namish Yadav**
- GitHub: [https://github.com/p3xz](https://github.com/p3xz)
- LinkedIn: [https://www.linkedin.com/in/namish-yadav-639769408/](https://www.linkedin.com/in/namish-yadav-639769408/)
- Instagram: [https://instagram.com/nam7sh](https://instagram.com/nam7sh)
