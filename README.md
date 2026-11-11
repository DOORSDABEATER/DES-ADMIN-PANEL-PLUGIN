# Dragon Empire Admin Panel

A starter Paper/Purpur 26.2 admin command-chain plugin. It provides `/adminpanel` (alias `/ap`), saved command chains, and console execution.

## Build automatically on GitHub

1. Create a new **public or private** GitHub repository.
2. Extract this ZIP on your PC.
3. Upload the **contents** of this folder to the repository root (make sure `.github` is included).
4. Open the repository's **Actions** tab and enable Actions if GitHub asks.
5. Choose **Build Dragon Empire Admin Panel** in the left sidebar, click **Run workflow**, then click the green **Run workflow** button.
6. When it finishes, open the successful run and download the `DragonEmpireAdminPanel-JAR` artifact.
7. Extract the downloaded artifact ZIP; inside is `DragonEmpireAdminPanel-1.0.0.jar`.
8. Put that JAR in your server's `plugins` folder and restart the server.

A push to the `main` branch also triggers a build automatically.

## Commands

- `/adminpanel` — open the GUI
- `/ap create <name>` — create a chain
- `/ap add <name> <command>` — append a command
- `/ap run <name>` — execute the chain as console
- `/ap delete <name>` — delete a chain
- `/ap list` — list chains

Example:

```text
/ap create event
/ap add event say The event is starting!
/ap add event tp @a 100 80 100
/ap run event
```

Only operators have the `dragonempire.admin` permission by default. Test chains on a test server or backup first: commands execute immediately with console permissions.
