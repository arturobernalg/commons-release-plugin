/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.release.plugin.mojos;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.release.plugin.SharedFunctions;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.AttachedArtifact;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * The purpose of this maven mojo is to detach the artifacts generated by the maven-assembly-plugin,
 * which for the Apache Commons Project do not get uploaded to Nexus, and putting those artifacts
 * in the dev distribution location for apache projects.
 *
 * @author chtompki
 * @since 1.0
 */
@Mojo( name = "detatch-distributions", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class CommonsDistributionDetatchmentMojo extends AbstractMojo {

    /**
     * A list of "artifact types" in the maven vernacular, to
     * be detatched from the deployment. For the time being we want
     * all artifacts generated by the maven-assembly-plugin to be detatched
     * from the deployment, namely *-src.zip, *-src.tar.gz, *-bin.zip,
     * *-bin.tar.gz, and the corresponding .asc pgp signatures.
     */
    private static final Set<String> ARTIFACT_TYPES_TO_DETATCH;
    static {
        Set<String> hashSet = new HashSet<>();
        hashSet.add("zip");
        hashSet.add("tar.gz");
        hashSet.add("zip.asc");
        hashSet.add("tar.gz.asc");
        ARTIFACT_TYPES_TO_DETATCH = Collections.unmodifiableSet(hashSet);
    }

    /**
     * This list is supposed to hold the maven references to the aformentioned artifacts so that we
     * can upload them to svn after they've been detatched from the maven deployment.
     */
    private List<AttachedArtifact> detatchedArtifacts = new ArrayList<>();

    /**
     * The maven project context injection so that we can get a hold of the variables at hand.
     */
    @Parameter( defaultValue = "${project}", required = true )
    private MavenProject project;

    /**
     */
    @Parameter( defaultValue = "${project.build.directory}/commons-release-plugin", alias = "outputDirectory" )
    private File workingDirectory;

    /**
     */
    @Parameter(required = true)
    private String distSvnStagingUrl;

    public void execute() throws MojoExecutionException {
        getLog().info("Detatching Assemblies");
        for (Object attachedArtifact : project.getAttachedArtifacts()) {
            if (ARTIFACT_TYPES_TO_DETATCH.contains(((AttachedArtifact) attachedArtifact).getType())) {
                detatchedArtifacts.add((AttachedArtifact) attachedArtifact);
            }
        }
        for(AttachedArtifact artifactToRemove : detatchedArtifacts) {
            project.getAttachedArtifacts().remove(artifactToRemove);
        }
        if (!workingDirectory.exists()) {
            SharedFunctions.initDirectory(getLog(), workingDirectory);
        }
        copyRemovedArtifactsToWorkingDirectory();
        getLog().info("");
        sha1AndMd5SignArtifacts();
    }

    private void copyRemovedArtifactsToWorkingDirectory() throws MojoExecutionException {
        StringBuffer copiedArtifactAbsolutePath;
        getLog().info("Copying detatched artifacts to working directory.");
        for (AttachedArtifact artifact: detatchedArtifacts) {
            File artifactFile = artifact.getFile();
            copiedArtifactAbsolutePath = new StringBuffer(workingDirectory.getAbsolutePath());
            copiedArtifactAbsolutePath.append("/");
            copiedArtifactAbsolutePath.append(artifactFile.getName());
            File copiedArtifact = new File(copiedArtifactAbsolutePath.toString());
            getLog().info("Copying: " + artifactFile.getName());
            SharedFunctions.copyFile(getLog(), artifactFile, copiedArtifact);
        }
    }

    private void sha1AndMd5SignArtifacts() throws MojoExecutionException {
        for (AttachedArtifact artifact : detatchedArtifacts) {
            if (!artifact.getFile().getName().contains("asc")) {
                try {
                    FileInputStream artifactFileInputStream = new FileInputStream(artifact.getFile());
                    String md5 = DigestUtils.md5Hex(artifactFileInputStream);
                    getLog().info(artifact.getFile().getName() + " md5: " + md5);
                    PrintWriter md5Writer = new PrintWriter(getMd5FilePath(workingDirectory, artifact.getFile()));
                    md5Writer.println(md5);
                    String sha1 = DigestUtils.sha1Hex(artifactFileInputStream);
                    getLog().info(artifact.getFile().getName() + " sha1: " + sha1);
                    PrintWriter sha1Writer = new PrintWriter(getSha1FilePath(workingDirectory, artifact.getFile()));
                    sha1Writer.println(sha1);
                    md5Writer.close();
                    sha1Writer.close();
                } catch (IOException e) {
                    throw new MojoExecutionException("Could not sign file: " + artifact.getFile().getName(), e);
                }
            }
        }
    }

    private String getMd5FilePath(File workingDirectory, File file) {
        StringBuffer buffer = new StringBuffer(workingDirectory.getAbsolutePath());
        buffer.append("/");
        buffer.append(file.getName());
        buffer.append(".md5");
        return buffer.toString();
    }

    private String getSha1FilePath(File workingDirectory, File file) {
        StringBuffer buffer = new StringBuffer(workingDirectory.getAbsolutePath());
        buffer.append("/");
        buffer.append(file.getName());
        buffer.append(".sha1");
        return buffer.toString();
    }
}
