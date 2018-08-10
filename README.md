# TIRO

TIRO is a hybrid iterative deobfuscation framework for Android applications.  Its name stands for the four steps: `Target`, `Instrument`, `Run`, and `Observe`.  TIRO's approach uses the idea of targeted execution (see our previous tool, [IntelliDroid](https://github.com/miwong/IntelliDroid)) to statically identify locations of possible obfuscation and determine the inputs to trigger these code locations.  By instrumenting these locations and executing them using the targeting information, run-time deobfuscation information can be gathered and passed back into static analysis to deobfuscate the application and achieve more complete analysis results.

For further details, please see our [paper](https://www.usenix.org/system/files/conference/usenixsecurity18/sec18-wong.pdf), published in Usenix Security 2018.

Currently, the code in this repository contains the base code for the `Target` step and is mainly a port of IntelliDroid's static component to the Soot framework.  We plan to release code for the other TIRO steps in the future.


## Static Analysis

TIRO's static analysis is comprised of the `Target` and `Instrument` steps.  In the `Target` step, locations of obfuscation are identified and call paths to those locations are extracted.  For each path, TIRO gathers constraints that determine the inputs that must be injected to trigger the path dynamically.  We currently use the Z3 constraint solver and convert constraints into the Z3-py and Z3-Java formats.


### Requirements

The static analysis component requires Java 8 (JDK 1.8).


### Cloning the repository

This repository contains a dependency to the `android-platforms` repository, which contains the Android framework libraries used by applications.  This dependency is a submodule within the project.  To clone TIRO with the submodule, run:

    git clone --recursive git@github.com:miwong/tiro.git

Alternatively, if you have already cloned TIRO without the submodule, run:

    git submodule update --init --recursive

This make take several minutes, as the Android framework libraries are large.

### Building and running

This project uses the Gradle build system.  Output files are located in the `build/` directory.  The gradlew script is a wrapper for machines that do not have Gradle already installed.  If your development machine already contains Gradle, you can use your own installation by replacing `./gradlew` with `gradle` in the commands below.  A network connection is required when compiling the code for the first time so that Gradle can automatically download dependencies. 

To build:

    ./gradlew build

To build and run:

    ./TIROStaticAnalysis <APK>

By default, TIRO writes the static analysis results into the `tiroOutput` directory.  The `-o` options allows this to be changed.  To see other command-line options, run:

    ./TIROStaticAnalysis --help


### Compatibility with IntelliDroid

Currently, TIRO's static analysis output (i.e. `appInfo.json`) is not compatible with IntelliDroid.  As we release the dynamic component of TIRO, we will also update the dynamic client used in IntelliDroid so that TIRO's constraint extraction can be integrated into dynamic analysis.


### Dependency on Soot and FlowDroid

TIRO is built on the Soot static analysis framework.  Several modifications were made to Soot's call-graph generation to enhance the call-graph with edges representing Android-specific execution flow (e.g. intents).  The modifications are published in a seperate repository [here](https://github.com/miwong/soot-tiro).

Since Android applications are event-driven, the entry-points into an application must be computed to achieve complete analysis.  TIRO uses the entry-point discovery implemented in [FlowDroid](https://github.com/secure-software-engineering/FlowDroid).  Minor modifications were made to this entry-point code to extract information required by TIRO's analysis; these changes are stored in `static/src/soot/` and `static/src/tiro/target/entrypoint/`.


## Contributors

The following have contributed code to TIRO:
* Michelle Wong
* Mariana D'Angelo


## Contact

TIRO was developed as a PhD project by Michelle Wong at the University of Toronto.  For any inquiries, please contact Michelle at michelley.wong@mail.utoronto.ca.


## License

TIRO is released under the [GNU Lesser General Public License, version 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html).

