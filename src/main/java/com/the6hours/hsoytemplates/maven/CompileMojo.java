package com.the6hours.hsoytemplates.maven;

import com.cadrlife.jhaml.JHamlParseException;
import com.google.template.soy.SoyFileSet;
import com.google.template.soy.base.SoySyntaxException;
import com.the6hours.hsoytemplates.HsoyFormatException;
import com.the6hours.hsoytemplates.HsoyJavaCompiler;
import com.the6hours.hsoytemplates.HsoyJsCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @goal compile
 * @phase process-sources
 *
 * @author Igor Artamonov (http://igorartamonov.com)
 * @since 23.09.12
 */
public class CompileMojo extends AbstractMojo implements Runnable{

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
     * Output Java dir
     *
     * @parameter property="outputJavaDir"
     */
    private File outputJavaDir;

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
    private String javaClass = "HsoyTemplates";

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
        getLog().info("Compile HAML-closures...");

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
        SoyFileSet soyFileSet;
        this.lastCompiled = new Date();
        try {
            soyFileSet = hsoyJsCompiler.build(files);
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
        if (generateJavascript && outputJavascriptFile != null) {
            compileJs(soyFileSet);
        }
        if (generateJava && outputJavaDir != null) {
            compileJava(soyFileSet);
        }
    }

    public void compileJs(SoyFileSet soyFileSet) {
        getLog().debug("Generate Javascript");
        HsoyJsCompiler jsCompiler = new HsoyJsCompiler();
        jsCompiler.setShouldGenerateJsdoc(shouldGenerateJsdoc);
        jsCompiler.setShouldProvideRequireSoyNamespaces(shouldProvideRequireSoyNamespaces);

        File outputDir = outputJavascriptFile.getParentFile();
        if (!outputJavascriptFile.exists()) {
            getLog().info("Create directory: " + outputDir);
            if (!outputDir.mkdirs()) {
                getLog().error("Failed to create directory " + outputDir);
            }
        } else if (!outputDir.isDirectory()) {
            getLog().error("Parent directory is a plain file: " + outputDir);
            return;
        }
        Writer writer = null;
        try {
            if (outputJavascriptFile.createNewFile()) {
                getLog().info("Created new file: " + outputJavascriptFile);
            }
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputJavascriptFile), "UTF-8"));
            String js = jsCompiler.compileToString(soyFileSet);
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

    public void compileJava(SoyFileSet soyFileSet) {
        getLog().debug("Generate Java");
        HsoyJavaCompiler javaCompiler = new HsoyJavaCompiler();
        javaCompiler.setTargetClass(javaClass);
        javaCompiler.setTargetPackage(javaPackage);

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
}
