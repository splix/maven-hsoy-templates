package com.the6hours.hamlclosures.maven;

import com.google.template.soy.SoyFileSet;
import com.google.template.soy.jssrc.SoyJsSrcOptions;
import com.the6hours.hamlclosures.HsoyFormatException;
import com.the6hours.hamlclosures.HsoyJsCompiler;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @goal compile
 * @phase process-sources
 *
 * @author Igor Artamonov (http://igorartamonov.com)
 * @since 23.09.12
 */
public class CompileMojo extends AbstractMojo {

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
     * Output file
     *
     * @parameter property="outputFile"
     */
    private File outputFile;

    public void execute() throws MojoExecutionException, MojoFailureException {
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

        HsoyJsCompiler hsoyJsCompiler = new HsoyJsCompiler();
        SoyFileSet soyFileSet;
        try {
            soyFileSet = hsoyJsCompiler.build(files);
        } catch (HsoyFormatException e) {
            getLog().error("Invalid format", e);
            return;
        } catch (IOException e) {
            getLog().error("Can't read files", e);
            return;
        }

        SoyJsSrcOptions jsSrcOptions = new SoyJsSrcOptions();
        jsSrcOptions.setShouldProvideRequireSoyNamespaces(shouldProvideRequireSoyNamespaces);
        jsSrcOptions.setShouldGenerateJsdoc(shouldGenerateJsdoc);
        List<String> compiledSrcs = soyFileSet.compileToJsSrc(jsSrcOptions, null);
        Writer writer = null;
        try {
            if (outputFile.createNewFile()) {
              getLog().info("Created new file: " + outputFile);
            }
            writer = new FileWriter(outputFile);
            for (String compiled: compiledSrcs) {
                writer.write(compiled);
                writer.flush();
            }
        } catch (IOException e) {
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

    public void setOutputFile(File outputFile) {
        this.outputFile = outputFile;
    }
}
