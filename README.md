# Intave

Intave is an enterprise and lightweight anticheat built on years of experience protecting the world's largest
Minecraft servers. See the full documentation [here](https://docs.intave.ac/).

For more information, you can join our discord [server](https://intave.ac/go/discord).

### Checks

Unlike traditional module-based anticheats, Intave accurately simulates player movement, client-side entity and block
data to detect even the smallest manipulations. Through this approach, Intave successfully prevents any kind of combat,
movement and interaction exploits, such as speed/fly cheats or reaching beyond the 3.0 block range.

Additionally, Intave provides heuristic checks to counter aimbot, auto-clicker, timer, placement, block breaking,
inventory
and many other cheats that cannot be detected by solely simulating client logic.

For more information, see the documentation of Intave's
checks [here](https://docs.intave.ac/mechanics/checks-01-overview.html).

## Development

### Project Setup

1. Clone the project: `git clone https://github.com/intave/Intave.git`.
2. Open the project as Gradle project; wait a few minutes for IntelliJ to index and build the
   project.

### Start a testserver

Choose one of the `run_X.X.X` gradle tasks corresponding to the Minecraft server version
you want to test. Intave is then automatically installed on that server. In case of Intave failing to download
ProtocolLib, make sure you manually install ProtocolLib on the server by moving it into the `plugins` directory.

By doing so, you can run the plugin directly in the IDE. Breakpoints and hotswapping is
enabled!
