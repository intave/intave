
![Intave](docs/assets/hero_banner.png "Intave")


Intave is an enterprise anticheat plugin for Minecraft servers in development since 2016.
After almost a decade of use on the world's largest Minecraft servers
and shutting down in mid-2025, we now decided to give back to the community by making Intave source-available to everyone.

## Downloads
- [Nightly](https://github.com/intave/intave/releases/download/nightly/Intave.jar)
- [Intave Loader](https://github.com/intave/loader/releases/download/v1.0.0/IntaveLoader.jar)

## General

Unlike traditional module-based anticheats, Intave accurately simulates player movement, client-side entity and block
data to detect even the smallest manipulations. Through this approach, Intave successfully prevents any kind of combat,
movement and interaction exploits, such as speed/fly cheats or reaching beyond the 3.0 block range.

Additionally, Intave provides heuristic checks to counter aimbot, auto-clicker, timer, placement, block breaking,
inventory
and many other cheats that cannot be detected by solely simulating client logic.

For more information, see the documentation of Intave's
checks [here](https://docs.intave.ac/mechanics/checks-01-overview.html).

## Development

### Setup

1. Clone the project: `git clone https://github.com/intave/intave.git`.
2. Open the project as Gradle project; wait a few minutes for IntelliJ to index and build the
   project.

### Testing

Choose one of the `intave/run_X.X.X` gradle tasks corresponding to the Minecraft server version
you want to test. Intave is then automatically installed on that server. In case of Intave failing to download
ProtocolLib, make sure you manually install ProtocolLib on the server by moving it into the `plugins` directory.

By doing so, you can run the plugin directly in the IDE. Breakpoints and hotswapping is
enabled!
We use [this IntelliJ plugin](https://plugins.jetbrains.com/plugin/14832-single-hotswap) for efficient hotswapping, which
can swap method contents that don't have an indy lambda or anonymous class.

## Contributing

We accept contributions to the project, but please make sure to read the [contributing guidelines](docs/CONTRIBUTING.md) before doing so.
For a high-level overview of the project organization, see [this document](docs/STRUCTURE.md).
A cheatsheet can be found [here](docs/CHEATSHEET.md) to quickly find your way around the codebase, contributions welcome!
Our block system is briefly outlined in [this document](docs/BLOCK_SYSTEM.md).
If you have any questions, feel free to get in touch with us on [Discord](https://intave.ac/go/discord).

## License
We want to make Intave completely free and open, available for everyone, indefinitely.
However, we don't want you or others to take this work, rebrand it and sell it as their own creation.
We've seen this happen multiple times with other anticheats, and we explicitly forbid this kind of behavior.
Still, we want to allow Minecraft servers commercial use of Intave and
the ability to modify and adapt it to their needs, as long as they don't sell it as a product or publish it.
Therefore, we decided to use the [Polyform Perimeter License 1.0.0](LICENSE.md),
prohibiting any form of competitive use.
We also want to encourage everyone to contribute back to the project instead of creating their personal spin-offs,
making the project better for everyone instead of fragmenting the community and development efforts.
This also technically means Intave isn't actually "open-source", but "source-available" for everyone to use and modify, but not to sell,
rebrand as their own or mix into their own product or project, no matter the respective licenses.
In case of source stealing or commercial redistribution we will be issuing DMCA takedowns and in blatant cases we will go 
the extra mile to bring legal action against you, we are not joking about this.
Please note that Intave uses third-party libraries, which are licensed under their respective licenses and
may not be covered by the Polyform Perimeter License.
