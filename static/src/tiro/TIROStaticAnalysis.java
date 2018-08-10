package tiro;

import soot.*;

import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import tiro.target.ManifestAnalysis;
import tiro.target.ResourceAnalysis;
import tiro.target.TargetedPathsAnalysis;
import tiro.target.callgraph.AndroidCallGraphPatching;
import tiro.target.dependency.DependencyAnalysis;
import tiro.target.entrypoint.EntryPointAnalysis;
import tiro.target.traversal.CallGraphTraversal;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.*;
import java.util.jar.JarFile;

public class TIROStaticAnalysis {
    public static class Configuration {
        public static final String Version = "0.2.0";
        public static long StartTime = 0;
        public static final long TargetedPathTimeout = 300000; // 5 minutes
        public static long Timeout = -1;

        public static Set<String> TargetMethods = new HashSet<String>();

        public static String ApkFile = null;
        public static List<String> DynamicFiles = new ArrayList<String>();
        public static String OutputDirectory = null;

        public static boolean MultiThreading = false;
        public static int NumberOfThreads = 8;

        public static boolean PrintSootOutput = false;
        public static boolean PrintOutput = true;
        public static boolean PrintConstraints = false;
    }

    public static Configuration Config = new Configuration();

    public static void main(String[] args) throws Exception {
        Options options = getCommandLineOptions();

        try {
            CommandLineParser commandLineParser = new DefaultParser();
            CommandLine commands = commandLineParser.parse(options, args, true);
            parseCommandLineOptions(options, commands);
        } catch (ParseException e) {
            System.err.println(e.toString());
            printHelp(options);
            System.exit(0);
        }

        Config.StartTime = System.currentTimeMillis();
        Output.progress("Starting analysis for " + Config.ApkFile + " at "
                + (new Date()).toString());
        TIROStaticAnalysis analysis = new TIROStaticAnalysis();
        analysis.analyze();
        Output.progress("Analysis completed successfully");
    }

    public void analyze() throws Exception {
        // Initialize soot options in case manifest analysis requires android.R$attr class
        initializeSoot();

        ManifestAnalysis manifestAnalysis = new ManifestAnalysis(
                TIROStaticAnalysis.Config.ApkFile);
        ResourceAnalysis resourceAnalysis = new ResourceAnalysis(
                TIROStaticAnalysis.Config.ApkFile);

        // Find entrypoints (note: performs/reset soot phases, must re-initialize soot after)
        Output.progress("Searching for entrypoints");
        EntryPointAnalysis entryPointAnalysis = new EntryPointAnalysis(
                manifestAnalysis, resourceAnalysis);

        // After this point, do not re-use any of the soot objects (the scene will be reset)!

        // Initialize soot options
        initializeSoot();

        // Add entrypoints
        Output.progress("Creating dummy main entrypoint method");
        Scene.v().setEntryPoints(Collections.singletonList(
                entryPointAnalysis.getDummyMainMethod()));

        // Soot packs used:
        //   wjpp     - add call graph patching tags for Android-specific call edges
        //   cg.spark - create call graph and points-to analysis
        //   wjtp     - main TIRO analysis (path extraction and constraint generation)

        PackManager.v().getPack("wjpp").add(new Transform("wjpp.AndroidCallGraphPatching",
                new AndroidCallGraphPatching(manifestAnalysis)));

        DependencyAnalysis dependencyAnalysis = new DependencyAnalysis(resourceAnalysis,
                entryPointAnalysis);
        TargetedPathsAnalysis targetedPathsAnalysis = new TargetedPathsAnalysis(
                manifestAnalysis, entryPointAnalysis, dependencyAnalysis);

        CallGraphTraversal callGraphTraversal = new CallGraphTraversal(entryPointAnalysis);
        callGraphTraversal.addPlugin(targetedPathsAnalysis.getCallGraphPlugin());
        dependencyAnalysis.getCallGraphPlugins().forEach(
                p -> { callGraphTraversal.addPlugin(p); });

        PackManager.v().getPack("wjtp").add(new Transform("wjtp.CallGraphTraversal",
                callGraphTraversal));

        PackManager.v().getPack("wjtp").add(new Transform("wjtp.TargetedPathsAnalysis",
                targetedPathsAnalysis));

        Output.progress("Generating call graph and points-to analysis");

        // Use runPacks() to manually produce output dex files using DexPrinter
        //soot.Main.main(new String[] {"-process-dir", Config.ApkFile});
        PackManager.v().runPacks();
    }

    public static void initializeSoot() {
        soot.G.reset();

        // Source format: APK
        soot.options.Options.v().set_src_prec(soot.options.Options.src_prec_apk);
        // Output format: None
        soot.options.Options.v().set_output_format(
                soot.options.Options.output_format_none);

        soot.options.Options.v().set_process_multiple_dex(true);
        soot.options.Options.v().set_soot_classpath("./libs/rt.jar");
        soot.options.Options.v().set_android_jars("./android-platforms");
        soot.options.Options.v().set_prepend_classpath(true);
        soot.options.Options.v().set_allow_phantom_refs(true);
        soot.options.Options.v().set_force_overwrite(true);
        soot.options.Options.v().set_whole_program(true);
        soot.options.Options.v().setPhaseOption("cg", "callgraph-tags:true");
        soot.options.Options.v().setPhaseOption("cg.spark", "on");
        soot.options.Options.v().setPhaseOption("cg.spark", "string-constants:true");

        // Suppress output
        if (!Config.PrintSootOutput) {
            try {
                G.v().out = new PrintStream(new File("/dev/null"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //soot.options.Options.v().setPhaseOption("cg", "verbose:true");

        // Exclude certain packages for better performance
        List<String> excludeList = new LinkedList<String>();
        excludeList.add("java.");
        excludeList.add("sun.misc.");
        excludeList.add("android.");
        excludeList.add("com.android.");
        excludeList.add("dalvik.system.");
        excludeList.add("org.apache.");
        excludeList.add("soot.");
        excludeList.add("javax.servlet.");
        soot.options.Options.v().set_exclude(excludeList);
        soot.options.Options.v().set_no_bodies_for_excluded(true);

        // Add code to be analyzed
        List<String> inputCode = new ArrayList<String>();
        inputCode.add(Config.ApkFile);

        soot.options.Options.v().set_process_dir(inputCode);

        Scene.v().loadNecessaryClasses();
    }

    private static Options getCommandLineOptions() {
        Options options = new Options();
        options.addOption(Option.builder("o").longOpt("output")
                .required(false).hasArg(true).argName("dir")
                .desc("Output directory for extracted paths and constraints "
                        + "(default: \"./tiroOutput\")")
                .build()
        );
        options.addOption(Option.builder("j").longOpt("multithreading")
                .required(false).hasArg(true).argName("threads")
                .desc("Enable multi-threaded analysis and set the number of threads")
                .build()
        );
        options.addOption(Option.builder("k").longOpt("timeout")
                .required(false).hasArg(true).argName("minutes")
                .desc("Time limit for analysis (best effort)")
                .build()
        );
        options.addOption(Option.builder("x").longOpt("nostdout")
                .required(false).hasArg(false)
                .desc("Do not print extracted paths in standard output")
                .build()
        );
        options.addOption(Option.builder("y").longOpt("constraints")
                .required(false).hasArg(false)
                .desc("Print extracted constraints in standard output")
                .build()
        );
        options.addOption(Option.builder("z").longOpt("sootOutput")
                .required(false).hasArg(false)
                .desc("Print output from Soot framework and FlowDroid entry-point extraction")
                .build()
        );
        options.addOption(Option.builder("h").longOpt("help")
                    .required(false).hasArg(false)
                    .desc("Print help")
                    .build()
        );
        options.addOption(Option.builder("v").longOpt("version")
                .required(false).hasArg(false)
                .desc("Print version")
                .build()
        );

        OptionGroup targetOptions = new OptionGroup();
        targetOptions.addOption(Option.builder("t").longOpt("targets")
                .required(false).hasArg(true).argName("file")
                .desc("Input file listing target methods for analysis "
                        + "(default: \"./targetedMethods.txt\")")
                .build()
        );

        options.addOptionGroup(targetOptions);
        return options;
    }

    private static void parseCommandLineOptions(Options options, CommandLine commands)
            throws Exception {
        if (commands.hasOption("h")) {
            printHelp(options);
            System.exit(0);
        }

        if (commands.hasOption("v")) {
            System.out.println("TIRO version: " + Config.Version);
            System.exit(0);
        }

        List<String> operands = commands.getArgList();
        if (operands.size() != 1) {
            throw new ParseException("Missing APK file", 0);
        }

        Config.ApkFile = operands.get(0);
        Config.OutputDirectory = commands.getOptionValue("o", "./tiroOutput");

        // Clean output directory
        try {
            File outputDirFile = new File(Config.OutputDirectory);
            outputDirFile.mkdirs();

            FileUtils.cleanDirectory(outputDirFile);
        } catch (Exception e) {
            Output.error(e.toString());
            e.printStackTrace();
        }

        if (commands.hasOption("j")) {
            Config.MultiThreading = true;

            try {
                Config.NumberOfThreads = Integer.parseInt(commands.getOptionValue("j"));
            } catch (Exception e) {
                System.err.println("Cannot parse multi-threading parameter");
                System.err.println("Exception: " + e.toString());
                System.exit(1);
            }

            if (Config.NumberOfThreads <= 1) {
                System.err.println("Warning: ignoring multi-threading parameter ("
                        + Config.NumberOfThreads + ")");
                Config.MultiThreading = false;
            }
        }

        if (commands.hasOption("k")) {
            try {
                Config.Timeout = 60000 * Integer.parseInt(commands.getOptionValue("k"));
            } catch (Exception e) {
                System.err.println("Cannot parse timeout parameter");
                System.err.println("Exception: " + e.toString());
                System.exit(1);
            }
        }

        if (commands.hasOption("x")) {
            Config.PrintOutput = false;
        }

        if (commands.hasOption("y")) {
            Config.PrintConstraints = true;
        }

        if (commands.hasOption("z")) {
            Config.PrintSootOutput = true;
        }

        String targetMethodsFile = commands.getOptionValue("t", "./targetedMethods.txt");
        //Output.log("Target: " + targetMethodsFile);

        try {
            BufferedReader br = new BufferedReader(new FileReader(targetMethodsFile));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                //String methodSignature = line.substring(line.indexOf("<") + 1,
                //    line.lastIndexOf(">"));
                String methodSignature = line;
                Config.TargetMethods.add(methodSignature);
            }

            br.close();

        } catch (Exception e) {
            System.err.println("Cannot read target methods file");
            System.err.println("Exception: " + e.toString());
            System.exit(1);
        }
    }

    private static void printHelp(Options options) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.printHelp("TIROStaticAnalysis [options] <APK>",
                "output: extracted paths and constraints in \"--output\" directory",
                options, "", false);
    }
}

