# OptiFine Custom Dye Colors Backport

Forge 1.8.9 client-side coremod that backports the modern OptiFine `dye.*`
custom color behavior for banner texture generation.

Put the built jar in the 1.8.9 Forge `mods` folder alongside OptiFine. Resource
packs can then set dye colors in:

```properties
assets/minecraft/mcpatcher/color.properties
```

Example:

```properties
dye.black=C74EBD
dye.red=C74EBD
dye.green=C74EBD
dye.brown=C74EBD
dye.blue=C74EBD
dye.purple=C74EBD
dye.cyan=C74EBD
dye.silver=C74EBD
dye.gray=C74EBD
dye.pink=C74EBD
dye.lime=C74EBD
dye.yellow=C74EBD
dye.lightBlue=C74EBD
dye.magenta=C74EBD
dye.orange=C74EBD
dye.white=C74EBD
```

The transformer patches 1.8.9's layered banner texture combiner so it asks
`DyeColorHooks` for banner dye colors. If a dye key is missing or invalid, the
vanilla 1.8.9 color is used.

## Build

The checked-in jar was compiled as Java 8 bytecode against local Forge
`1.8.9-11.15.1.2318-1.8.9`, Launchwrapper `1.12`, and ASM `5.0.3`.

```powershell
$forge = "$env:APPDATA\.minecraft\libraries\net\minecraftforge\forge\1.8.9-11.15.1.2318-1.8.9\forge-1.8.9-11.15.1.2318-1.8.9.jar"
$lw = "$env:APPDATA\.minecraft\libraries\net\minecraft\launchwrapper\1.12\launchwrapper-1.12.jar"
$asm = "$env:APPDATA\.minecraft\libraries\org\ow2\asm\asm-all\5.0.3\asm-all-5.0.3.jar"
New-Item -ItemType Directory -Force -Path build\classes, build\libs
javac --release 8 -classpath "$forge;$lw;$asm" -d build\classes (Get-ChildItem src\main\java -Recurse -Filter *.java).FullName
jar cfm build\libs\optifinecustomcolors-1.0.0-mc1.8.9.jar src\main\resources\META-INF\MANIFEST.MF -C build\classes .
```
