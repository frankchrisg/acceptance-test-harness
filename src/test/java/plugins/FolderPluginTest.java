/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package plugins;

import org.apache.commons.io.IOUtils;
import org.hamcrest.MatcherAssert;
import org.jenkinsci.test.acceptance.Matchers;
import org.jenkinsci.test.acceptance.junit.AbstractJUnitTest;
import org.jenkinsci.test.acceptance.junit.WithPlugins;
import org.jenkinsci.test.acceptance.plugins.credentials.CredentialsPage;
import org.jenkinsci.test.acceptance.plugins.credentials.ManagedCredentials;
import org.jenkinsci.test.acceptance.plugins.credentials.UserPwdCredential;
import org.jenkinsci.test.acceptance.po.*;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jenkinsci.test.acceptance.Matchers.containsRegexp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for the CloudBees Folder Plugins.
 */
@WithPlugins("cloudbees-folder")
public class FolderPluginTest extends AbstractJUnitTest {
    /** Test folder name. */
    private static final String F01 = "F01";
    /** Test folder name. */
    private static final String F02 = "F02";
    /** Test view names. */
    private static final String ALL_VIEW = "All";
    private static final String MY_VIEW = "MyView";
    /** Test job names. */
    private static final String JOB1 = "job1";
    private static final String JOB2 = "job2";
    /** Test properties data. */
    private final static String PROPERTY_NAME = "myProperty";
    private final static String PROPERTY_VALUE = "myValue";
    /** Credential data. */
    private final static String CRED_ID = "mycreds";
    private final static String CRED_USER = "myUser";
    private final static String CRED_PASS = "myPass";
    private final static String CRED_INJECTED_MESSAGE = "credentials have been injected";
    
    /**
     * Checks that a folder exists and has the provided name.
     */
    private void checkFolder(Folder folder, String name) {
        folder.open();
        MatcherAssert.assertThat(driver, Matchers.hasContent(name));
    }
    
    /**
     * First simple test scenario: Folder creation (JENKINS-31648).
     * <ol>
     * <li>We create a folder named "F01".</li>
     * <li>We check the folder exists and we can enter it.</li>
     * </ol>
     */
    @Test
    public void createFolder() {
        final Folder folder = jenkins.jobs.create(Folder.class, F01);
        folder.save();
        jenkins.open();
        checkFolder(folder, F01);
    }
    
    /**
     * Simple folder hierarchy test scenario: Folder creation (JENKINS-31648).
     * <ol>
     * <li>We create a folder named "F01".</li>
     * <li>We check the folder exists and we can enter it.</li>
     * <li>We create a folder name "F02" inside "F01" and check it.</li>
     * <li>We visit "F01" and the root page, create a folder named "F01" inside the existing "F01" one and check it.
     * </ol>
     */
    @Test
    public void createFolderHierarchy() {
        final Folder parent = jenkins.jobs.create(Folder.class, F01);
        parent.save();
        checkFolder(parent, F01);
        final Folder child1 = parent.getJobs().create(Folder.class, F02);
        child1.save();
        checkFolder(child1, F02);
        parent.open();
        jenkins.open();
        final Folder child2 = parent.getJobs().create(Folder.class, F01);
        child2.save();
        checkFolder(child2, F01);
    }

    /**
     * Simple test scenario to validate views in a folder are working properly.
     */
    @Test
    public void folderViewsTest() {
        final Folder folder = jenkins.jobs.create(Folder.class, F01);
        folder.open();

        this.checkViews(folder, ALL_VIEW, ALL_VIEW);

        ListView myView = folder.getViews().create(ListView.class, MY_VIEW);
        myView.open();

        this.checkViews(folder, MY_VIEW, ALL_VIEW, MY_VIEW);

        folder.selectView(ListView.class, ALL_VIEW);

        this.checkViews(folder, ALL_VIEW);

        myView = folder.selectView(ListView.class, MY_VIEW);

        this.checkViews(folder, MY_VIEW);

        myView.delete();

        this.checkViews(folder, ALL_VIEW, ALL_VIEW);
    }

    /**
     * Test scenario to validate credentials scoped in folders.
     */
    @Test
    @WithPlugins({ "credentials", "credentials-binding", "workflow-job", "workflow-cps", "workflow-basic-steps", "workflow-durable-task-step" })
    public void credsTest() {
        final Folder folder = jenkins.jobs.create(Folder.class, F01);
        final FreeStyleJob job1 = folder.getJobs().create(FreeStyleJob.class, JOB1);

        this.checkItemExists(folder, true);
        this.checkItemExists(job1, true);

        final WorkflowJob job2 = createWorkflowJob(folder);
        this.checkItemExists(job2, true);

        final String console = job2.startBuild().shouldSucceed().getConsole();
        assertThat(console, containsRegexp(CRED_INJECTED_MESSAGE));

        job1.delete();
        this.checkItemExists(job1, false);

        job2.delete();
        this.checkItemExists(job2, false);

        folder.delete();
        this.checkItemExists(folder, false);
    }

    private void checkViews(final Folder f, final String expectedActiveView, final String... expectedExistingViews) {
        if (expectedExistingViews.length > 0) {
            final List<String> viewNames = f.getViewsNames();
            assertEquals(expectedExistingViews.length, viewNames.size());

            for (final String expectedView : expectedExistingViews) {
                assertTrue(viewNames.contains(expectedView));
            }
        }

        final String activeView = f.getActiveViewName();
        assertEquals(expectedActiveView, activeView);
    }

    private void checkItemExists(final TopLevelItem f, final boolean expectedExists) {
        boolean exists = true;

        try {
            IOUtils.toString(f.url("").openStream());
        } catch (final IOException ex) {
            exists = false;
        }

        assertEquals(exists, expectedExists);
    }

    private void createCredentials(final Folder f) {
        final CredentialsPage mc = new CredentialsPage(f, ManagedCredentials.DEFAULT_DOMAIN);
        mc.open();

        final UserPwdCredential cred = mc.add(UserPwdCredential.class);
        cred.username.set(CRED_USER);
        cred.password.set(CRED_PASS);
        cred.setId(CRED_ID);

        mc.create();
    }

    private WorkflowJob createWorkflowJob(final Folder f) {
        // Create folder credentials
        this.createCredentials(f);

        final String script = String.format("node {\n" +
                "    withCredentials([usernamePassword(credentialsId: '%s', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {\n" +
                "        sh '[ \"$USERNAME\" = \"%s\" ] && [ \"$PASSWORD\" = \"%s\" ] && echo \"%s\"'\n" +
                "    }\n" +
                "}",
                CRED_ID, CRED_USER, CRED_PASS, CRED_INJECTED_MESSAGE);

        final WorkflowJob job = f.getJobs().create(WorkflowJob.class, JOB2);
        job.script.set(script);
        job.save();

        return job;
    }

}
