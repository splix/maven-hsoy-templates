package com.the6hours.hsoytemplates.maven;

import com.cadrlife.jhaml.JHamlParseException;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.base.SoySyntaxException;
import com.the6hours.hsoytemplates.HsoyFormatException;
import com.the6hours.hsoytemplates.HsoyJavaCompiler;
import com.the6hours.hsoytemplates.HsoyJsCompiler;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 *
 * @goal compile
 * @phase process-sources
 *
 * @author Igor Artamonov (http://igorartamonov.com)
 * @since 23.09.12
 */
public class CompileMojo extends AbstractMojo implements Runnable {

    private static final Pattern HSOY_FILE = Pattern.compile("(\\.hsoy)$");

    /**
     * The haml-closure files to be compiled into javascript files.
     *
     * @parameter property="inputFiles"
     * @required
     */
    private FileSet inputFiles;

    /**
     * Whether to generate code with provide/require soy namespaces for Google Closure Compiler
     * integration.
     *
     * @parameter property="shouldProvideRequireSoyNamespaces" default-value="false"
     */
    private boolean shouldProvideRequireSoyNamespaces;

    /**
     * Whether to generate Jsdoc compatible for Google Closure Compiler integration.
     *
     * @parameter property="shouldGenerateJsdoc" default-value="false"
     */
    private boolean shouldGenerateJsdoc;

    /**
     * Output Javascript file
     *
     * @parameter property="outputJavascriptFile"
     */
    private File outputJavascriptFile;

    /**
     * Output Javascript dir
     *
     * @parameter property="outputJavascriptDir"
     */
    private File outputJavascriptDir;

    /**
     * Output Java dir
     *
     * @parameter property="outputJavaDir"
     */
    private File outputJavaDir;

    /**
     * The current project base directory.
     *
     * @parameter expression="${basedir}"
     * @required
     */
    private String basedir;

    /**
     * Refresh period, in seconds
     *
     * @parameter property="refreshPeriod" default-value="5"
     */
    private int refreshPeriod = 5;

    /**
     * Enable JS generator
     *
     * @parameter property="generateJavascript" default-value="true"
     */
    private boolean generateJavascript = true;

    /**
     * Enable Java generator
     *
     * @parameter property="generateJava" default-value="true"
     */
    private boolean generateJava = true;

    /**
     * Setup package for generated Java classes
     *
     * @parameter property="javaPackage" default-value="hsoy"
     */
    private String javaPackage = "hsoy";

    /**
     * Setup class name for generated Java classes
     *
     * @parameter property="javaClass" default-value="HsoyTemplates"
     */
    private String javaClass = null;

    /**
     * Add content of specified file will be used as a template for generated JS
     *
     * @parameter property="javascriptTemplate"
     */
    private File javascriptTemplate = null;


    // ============
    // local fields
    // ============

    private Date lastCompiled;

    private List<File> watch;

    private boolean alive = true;

    public void execute() throws MojoExecutionException, MojoFailureException {
        compile();

        Thread refresh = new Thread(this);
        refresh.start();
    }

    private boolean isModified() {
        if (watch == null) {
            return false;
        }
        for (File f: watch) {
            if (!f.exists()) {
                return true;
            }
            if (f.lastModified() > lastCompiled.getTime()) {
                return true;
            }
        }
        return false;
    }

    public void run() {
        while (alive) {
            try {
                Thread.sleep(1000L * refreshPeriod);
            } catch (InterruptedException e) {
                getLog().warn("Sleep interrupted");
            }
            if (isModified()) {
                compile();
            }
        }
    }

    private void compile() {
        getLog().info("Compile Hsoy Templates");

        FileSetManager fileSetManager = new FileSetManager(getLog());
        List<File> files = new ArrayList<File>();
        for (String filename : fileSetManager.getIncludedFiles(inputFiles)) {
            File soyFile = new File(inputFiles.getDirectory(), filename);
            getLog().info("Including hsoy file: " + soyFile);
            if (!soyFile.exists()) {
                getLog().error(String.format("File %s doesn't exists", filename));
            }
            files.add(soyFile);
        }

        this.watch = files;

        HsoyJsCompiler hsoyJsCompiler = new HsoyJsCompiler();
        SoyFileSet soyAllFilesSet;
        this.lastCompiled = new Date();
        try {
            soyAllFilesSet = hsoyJsCompiler.build(files);
        } catch (HsoyFormatException e) {
            getLog().error("Invalid hsoy format", e);
            return;
        } catch (JHamlParseException e) {
            getLog().error("Invalid HAML format", e);
            return;
        } catch (IOException e) {
            getLog().error("Can't read files", e);
            return;
        }

        boolean joinJs = generateJavascript && outputJavascriptFile != null;
        boolean joinJava = generateJava && outputJavaDir != null && StringUtils.isNotEmpty(javaClass);
        List<SoyFileSet> soys = new ArrayList<SoyFileSet>(files.size());
        String base = new File(inputFiles.getDirectory()).getAbsolutePath();

        for (File file: files) {
            SoyFileSet soy = null;
            try {
                soy = hsoyJsCompiler.build(file);
                soys.add(soy);
            } catch (HsoyFormatException e) {
                getLog().error("Invalid hsoy format", e);
            } catch (JHamlParseException e) {
                getLog().error("Invalid HAML format", e);
            } catch (IOException e) {
                getLog().error("Can't read files", e);
            }
            if (soy != null) {
                if (!joinJs) {
                    compileJs(soy, targetFile(file, outputJavascriptDir, generateFilename(file.getName(), ".js")));
                }
                if (!joinJava) {
                    String dir = file.getParentFile().getAbsolutePath();
                    String relativeDir = dir.substring(base.length() + 1);
                    String subpackage = null;
                    if (relativeDir.length() > 1) {
                        subpackage = relativeDir.replaceAll(File.separator, ".");
                    }
                    String justName = file.getName().substring(0, file.getName().indexOf('.'));
                    String javaClass = StringUtils.capitalize(justName);
                    compileJava(soy, subpackage, javaClass);
                }
            } else {
                getLog().warn("Skip compilation for " + file.getName());
            }
        }

        if (joinJs) {
            compileJs(soyAllFilesSet);
        }
        if (joinJava) {
            compileJava(soyAllFilesSet, null, javaClass);
        }
    }

    private File targetFile(File src, File targetBaseDir, String filename) {
        File srcDir = src.getParentFile();
        File baseDir = new File(inputFiles.getDirectory());
        String subpath = srcDir.getAbsolutePath().substring(baseDir.getAbsolutePath().length());
        File targetDir = new File(targetBaseDir, subpath);
        return new File(targetDir, filename);
    }


    private String generateFilename(String src, String suffix) {
        return HSOY_FILE.matcher(src).replaceAll(suffix);
    }

    public void compileJs(SoyFileSet soyFileSet) {
        compileJs(soyFileSet, outputJavascriptFile);
    }

    public void compileJs(SoyFileSet soyFileSet, File outputJavascriptFile) {
        getLog().debug("Generate Javascript");
        HsoyJsCompiler jsCompiler = new HsoyJsCompiler();
        jsCompiler.setShouldGenerateJsdoc(shouldGenerateJsdoc);
        jsCompiler.setShouldProvideRequireSoyNamespaces(shouldProvideRequireSoyNamespaces);

        File outputDir = outputJavascriptFile.getParentFile();
        if (!outputDir.exists()) {
            getLog().info("Create directory: " + outputDir);
            if (!outputDir.mkdirs()) {
                getLog().error("Failed to create directory " + outputDir);
            }
        } else if (!outputDir.isDirectory()) {
            getLog().error("Parent directory is a plain file: " + outputDir);
            return;
        }
        List<String> header = new ArrayList<String>();
        List<String> footer = new ArrayList<String>();
        if (javascriptTemplate != null) {
            if (javascriptTemplate.exists() && javascriptTemplate.isFile()) {
                boolean foundMarker = false;
                BufferedReader rdr = null;
                try {
                    rdr = new BufferedReader(new FileReader(javascriptTemplate));
                    String line;
                    List<String> buffer = header;
                    while ((line = rdr.readLine()) != null ) {
                        String clean = line.trim().replaceAll("\\s", "");
                        if (clean.equals("$hsoy.body") || clean.equals("$hsoy.body;")) {
                            foundMarker = true;
                            buffer = footer;
                        } else {
                            buffer.add(line);
                        }
                    }
                } catch (IOException e) {
                    getLog().warn("Can't read JS Template", e);
                } finally {
                    if (rdr != null) {
                        try {
                            rdr.close();
                        } catch (IOException e) {
                            getLog().warn("Can't close JS Template file", e);
                        }
                    }
                }
                if (!foundMarker) {
                    getLog().warn("Didn't find Hsoy Template marker (a line '//HSOY') inside template: " + javascriptTemplate.getAbsolutePath());
                }
            } else {
                getLog().warn("Can't read JS Template, file doesn't exist or not a file: " + javascriptTemplate.getAbsolutePath());
            }
        }
        Writer writer = null;
        try {
            if (outputJavascriptFile.createNewFile()) {
                getLog().info("Created new file: " + outputJavascriptFile);
            }
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputJavascriptFile), "UTF-8"));
            for(String line: header) {
                writer.write(line);
                writer.write("\n");
            }
            String js = jsCompiler.compileToString(soyFileSet);
            writer.write(js);
            for(String line: footer) {
                writer.write(line);
                writer.write("\n");
            }
            writer.flush();
        } catch (IOException e) {
            getLog().error("Can't generate JS file", e);
        } catch (SoySyntaxException e) {
            getLog().error("Invalid Soy", e);
        } catch (HsoyFormatException e) {
            getLog().error("Invalid Hsoy", e);
        } catch (Exception e) {
            getLog().error("Cannot compile Java code for Hsoy Template", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e1) {
                }
            }
        }
    }

    public void compileJava(SoyFileSet soyFileSet, String subpackage, String javaClass) {
        getLog().debug("Generate Java");
        HsoyJavaCompiler javaCompiler = new HsoyJavaCompiler();
        javaCompiler.setTargetClass(javaClass);
        String packageName = subpackage != null ? StringUtils.join(Arrays.asList(javaPackage, subpackage), ".") : javaPackage;
        javaCompiler.setTargetPackage(packageName);

        if (this.outputJavaDir == null) {
            this.outputJavaDir = new File(basedir, "src/main/java");
        }
        File outputJavaDir = new File(this.outputJavaDir, packageName.replaceAll("\\.", File.separator));

        if (!outputJavaDir.exists()) {
            getLog().info("Create directory: " + outputJavaDir);
            if (!outputJavaDir.mkdirs()) {
                getLog().error("Failed to create directory " + outputJavaDir);
            }
        } else if (!outputJavaDir.isDirectory()) {
            getLog().error("Parent directory is a plain file: " + outputJavaDir);
            return;
        }
        File outputFile = new File(outputJavaDir, javaClass + ".java");
        Writer writer = null;
        try {
            if (outputFile.createNewFile()) {
                getLog().info("Created new file: " + outputFile);
            }
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"));
            String js = javaCompiler.compileToString(soyFileSet);
            writer.write(js);
            writer.flush();
        } catch (IOException e) {
            getLog().error("Can't generate JS file", e);
        } catch (SoySyntaxException e) {
            getLog().error("Invalid Soy", e);
        } catch (HsoyFormatException e) {
            getLog().error("Invalid Hsoy", e);
        } catch (Exception e) {
            getLog().error("Cannot compile Java code for Hsoy Template", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e1) {
                }
            }
        }
    }

    public void setInputFiles(FileSet inputFiles) {
        this.inputFiles = inputFiles;
    }

    public void setShouldProvideRequireSoyNamespaces(boolean shouldProvideRequireSoyNamespaces) {
        this.shouldProvideRequireSoyNamespaces = shouldProvideRequireSoyNamespaces;
    }

    public void setShouldGenerateJsdoc(boolean shouldGenerateJsdoc) {
        this.shouldGenerateJsdoc = shouldGenerateJsdoc;
    }

    public void setRefreshPeriod(int refreshPeriod) {
        this.refreshPeriod = refreshPeriod;
    }

    public void setOutputJavascriptFile(File outputJavascriptFile) {
        this.outputJavascriptFile = outputJavascriptFile;
    }

    public void setOutputJavascriptDir(File outputJavascriptDir) {
        this.outputJavascriptDir = outputJavascriptDir;
    }

    public void setOutputJavaDir(File outputJavaDir) {
        this.outputJavaDir = outputJavaDir;
    }

    public void setGenerateJavascript(boolean generateJavascript) {
        this.generateJavascript = generateJavascript;
    }

    public void setGenerateJava(boolean generateJava) {
        this.generateJava = generateJava;
    }

    public void setJavaPackage(String javaPackage) {
        this.javaPackage = javaPackage;
    }

    public void setJavaClass(String javaClass) {
        this.javaClass = javaClass;
    }

    public void setJavascriptTemplate(File javascriptTemplate) {
        this.javascriptTemplate = javascriptTemplate;
    }
}
