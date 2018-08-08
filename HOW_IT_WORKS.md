# What happens when Maven build is run

If `em-maven-extension` is added to the project like this: 
```
    <build>
        <extensions>
            <extension>
                <groupId>com.commsen.em</groupId>
                <artifactId>em-maven-extension</artifactId>
                <version>${em.version}</version>
            </extension>
        </extensions>
    </build>
``` 
then the `EccentricModularityMavenLifecycleParticipant` is executed.

**For each project in the reactor** `EccentricModularityMavenLifecycleParticipant` calls `EccentricModularityExecutionListener` which does the following:

1. checks for and warns of old properties _(there was properties syntax change at some point)_
1. If SNAPSHOT version of `Bnd` is used it adds `Bnd`'s snapshot repository to the list of remote repositories so Maven can download needed artifacts.
1. It dynamically adds `em-maven-plugin:link` plugin goal to `package` phase. [Here is what it does](#what-em-maven-pluginlink-goal-does) when it is executed later on.
1. If `<em:module />` property is found in the POM:
    1. adds `em-maven-plugin:registerContract` plugin goal to `package` phase. [Here is what it does](#what-em-maven-pluginregistercontract-goal-does) when it is executed later on.
    1. [configures](#how-is-bnd-maven-plugin-dynamically-configured) `bnd-maven-plugin` and dynamically adds it to the POM. When executed during the `package` phase this plugin generates OSGi metadata in the resulting jar file. For more information see https://github.com/bndtools/bnd/tree/master/maven/bnd-maven-plugin 
1. If `<em:augment />` property is found in the POM:
    1. TODO
1. If project's packaging in `index`
    1. TODO
1. If `<em:resolve />` property is found in the POM:
    1. If the property has value it is treated as target runtime and "distro jar" _(matadata only)_ for it is created in EM's home of the project.
    1. adds `em-maven-plugin:registerContract` plugin goal to `package` phase. [Here is what it does](#what-em-maven-pluginregistercontract-goal-does) when it is executed later on.
    1. [configures](#how-is-em-maven-pluginresolve-dynamically-configured) `em-maven-plugin:resolve` and dynamically adds it to the POM. When executed during the `package` phase this plugin [resolves what modles are needed](#what-em-maven-pluginresolve-goal-does) and then places the respective jar files in the target folder
    1. indexer
    1. [configures](#how-is-bnd-maven-plugin-dynamically-configured) `bnd-maven-plugin` and dynamically adds it to the POM. When executed during the `package` phase this plugin generates OSGi metadata in the resulting jar file. For more information see https://github.com/bndtools/bnd/tree/master/maven/bnd-maven-plugin 
1. If `<em:executable />` property is found in the POM:
    1. TODO



# What `em-maven-plugin:link` goal does

The goal is executed during `package` stage for all modules _(maven projects)_ that are part of the build.

1. It copies the resulting jar file to project's `EM_HOME` (by default that is `~/.em/<GROUP_ID>/<ARTIFACT_ID>/<VERSION>/<JAR_FILE_NAME>`)
2. It updates EM's local database creating a mapping between 
    - the exact location of the jar file in the project's target folder
    - the exact location of the jar file in Maven'l local repo _(the `~/.m2/repository/...`)_
    - the exact location of the jar file in EM's home folder     

# What `em-maven-plugin:registerContract` goal does

The goal is executed during `package` stage for all modules _(maven projects)_ that are part of the build and have one of the following properties:

- `<em:module />`
- `<em:resolve />`
- `<em:executable />`

It inspects the resulting jar file for capabilities in the `em.contract` namespace. If such capabilities are found they are saved in EM's local database. 

# How is `bnd-maven-plugin` dynamically configured

1. A `bnd` instructions are generated from [this freemarker template](em-maven-extension/src/main/resources/META-INF/templates/bnd.fmt) where:
    - `includePackages` is taken "as is" from `<em:module.includePackages />` POM property 
    - `importStatement` is calculated based on `<em:module.importPackages />` and `<em:module.ignorePackages />` POM properties. 
1. The generated instructions are added to the `bnd-maven-plugin`'s configuration and the plugin is dynamically added to the POM
1. The `maven-compiler-plugin` is dynamically reconfigured to add `em.annotation.processors` to it's `annotationProcessorPaths`. [Here is what they do](#what-ems-annotation-processors-do) when executed during `compile` phase.
1. The `maven-jar-plugin` is dynamically reconfigured to use the `MANIFEST.MF` generated by `bnd-maven-plugin`

# What EM's annotation processors do

- add "`Provide-Capability: ...`" to generated bnd file for modules having classes annotated with `@Provides` or custom annotations meta-annotated with `@Provides`
- add "`Require-Capability: ...`" to generated bnd file for modules having classes annotated with `@Requires` or custom annotations meta-annotated with `@Requires`
- add "`Fragment-Host: system.bundle; extension:=framework`" to generated bnd file for modules having class annotated with `@RuntimeExtension`
- add "`Fragment-Host: system.bundle; extension:=bootclasspath`" to generated bnd file for modules having class annotated with `@RuntimeExtension(Type.BOOTCLASSPATH)`
- add "`Bundle-Activator: ...`" to generated bnd file for modules having classes annotated with `@Activator`
- add "`ExtensionBundle-Activator: ...`" to generated bnd file for modules having classes annotated with `@Activator(extension=true)`

# How is `em-maven-plugin:resolve` dynamically configured
The plugin configuration is generated from [this freemarker template](em-maven-extension/src/main/resources/META-INF/templates/bnd-export-maven-plugin-configuration.fmt) where:

- `bundlesOnly` is `true` if the goal was added by `<em:resolve />` and `false` if the goal was added by `<em:executable />`
- `bndrun` is the file to be used by the bnd's resolve/export process:
    1. If the file is provided in `<em:config.bndrunFile>` property and it exists and it's a file, this file is used.
    2. If there is no file specified, it is generated from [this freemarker template](em-maven-extension/src/main/resources/META-INF/templates/bndrun.fmt) where:
        - `requirements` is a list of generated contract requirements containing 
            - the module itself 
            - the contracts provided in `<em:contracts>` POM property
        - `runProperties` contains the runtime properties specified in `<em:executable.properties>` POM property
        - `distro` is the "distro jar" (matadata only) generated from the provided target environment if specified.

# What `em-maven-plugin:resolve` goal does

This mojo extends the [bnd-export-maven-plugin](https://github.com/bndtools/bnd/tree/master/maven/bnd-export-maven-plugin) mojo and does the following:

1. 

# TODO