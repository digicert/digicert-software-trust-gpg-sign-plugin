//The MIT License
//
//Copyright 2023
//
//Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
//
//The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
//
//THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

package io.jenkins.plugins.digicert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;

public class Linux {

    private final TaskListener listener;

    private final String SM_HOST;
    // lgtm[jenkins/plaintext-storage]
    private final String SM_API_KEY;
    private final String SM_CLIENT_CERT_FILE;
    // lgtm[jenkins/plaintext-storage]
    private final String SM_CLIENT_CERT_PASSWORD;
    private final String pathVar;
    private final static String prompt = "bash";
    private final static char c = '-';

    String dir = System.getProperty("user.dir");
    ProcessBuilder processBuilder = new ProcessBuilder();
    private Integer result;

    public Linux(TaskListener listener, String SM_HOST, String SM_API_KEY, String SM_CLIENT_CERT_FILE,
            String SM_CLIENT_CERT_PASSWORD, String pathVar) {

        this.listener = listener;

        this.SM_HOST = SM_HOST;

        this.SM_API_KEY = SM_API_KEY;

        this.SM_CLIENT_CERT_FILE = SM_CLIENT_CERT_FILE;

        this.SM_CLIENT_CERT_PASSWORD = SM_CLIENT_CERT_PASSWORD;

        this.pathVar = pathVar;

    }

    public Integer install(String os) {

        this.listener.getLogger().println("\nAgent type: " + os);

        String host = SM_HOST.trim().substring(19).replaceAll("/$", "");
        String smctlDownloadUrl = String.format(
                "https://%s/signingmanager/api-ui/v1/releases/noauth/smtools-linux-x64.tar.gz/download",
                host);

        this.listener.getLogger()
                .println("\nInstalling SMCTL from: " + smctlDownloadUrl);
        result = executeCommand("curl -X GET " + smctlDownloadUrl + " -o smtools-linux-x64.tar.gz");
        if (result != 0) {
            return result;
        }

        result = executeCommand("tar xvf smtools-linux-x64.tar.gz > /dev/null");

        if (result == 0)

            this.listener.getLogger().println("\nSMCTL Installation Complete\n");

        else {

            this.listener.getLogger().println("\nSMCTL Installation Failed\n");

            return result;

        }

        dir = dir + File.separator + "smtools-linux-x64";

        String scdDownloadUrl = String
                .format("https://%s/signingmanager/api-ui/v1/releases/noauth/ssm-scd-linux-x64/download", host);
        this.listener.getLogger().println("\nInstalling SCD from: " + scdDownloadUrl);
        result = executeCommand("curl -X GET " + scdDownloadUrl + " -o ssm-scd");

        if (result == 0)
            this.listener.getLogger().println("\nSCD Installation Complete\n");
        else {
            this.listener.getLogger().println("\nSCD Installation Failed\n");
            return result;
        }

        String userName = System.getProperty("user.name");

        if (userName.equals("root")) {
            result = executeCommand("chmod -R +x " + dir);
        } else {
            result = executeCommand("sudo chmod -R +x " + dir);
        }

        return result;
    }

    public Integer createFile(String path, String str) {

        File file = new File(path); // initialize File object and passing path as argument
        FileOutputStream fos = null;
        try {

            if (file.createNewFile())
                ;
            try {

                String name = file.getCanonicalPath();

                fos = new FileOutputStream(name, false); // true for append mode

                byte[] b = str.getBytes(StandardCharsets.UTF_8); // converts string into bytes

                fos.write(b); // writes bytes into file

                fos.close(); // close the file

                return 0;

            } catch (Exception e) {
                if (fos != null)
                    fos.close();
                e.printStackTrace(this.listener.error(e.getMessage()));

                return 1;

            }

        } catch (IOException e) {

            e.printStackTrace(this.listener.error(e.getMessage()));

            return 1;

        }

    }

    public void setEnvVar(String key, String value) {

        try {

            Jenkins instance = null;
            try {
                instance = Jenkins.get();
            } catch (IllegalStateException e) {
                this.listener.getLogger().println("Could not set environment variable: " + key + " with value: " + value
                        + ". This is due to the plugin running on a slave node. This will have to be manually defined in the pipeline as an environment variable.");
            }

            if (instance != null) {
                DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = instance
                        .getGlobalNodeProperties();

                List<EnvironmentVariablesNodeProperty> envVarsNodePropertyList = globalNodeProperties
                        .getAll(EnvironmentVariablesNodeProperty.class);

                EnvironmentVariablesNodeProperty newEnvVarsNodeProperty = null;

                EnvVars envVars = null;

                if (envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0) {

                    newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();

                    globalNodeProperties.add(newEnvVarsNodeProperty);

                    envVars = newEnvVarsNodeProperty.getEnvVars();

                } else {

                    envVars = envVarsNodePropertyList.get(0).getEnvVars();

                }

                envVars.put(key, value);

                instance.save();
            }
        } catch (IOException e) {
            e.printStackTrace(this.listener.error(e.getMessage()));
        }

    }

    public Integer executeCommand(String command) {

        try {

            processBuilder.command(prompt, c + "c", command);

            Map<String, String> env = processBuilder.environment();

            if (SM_API_KEY != null)

                env.put(Constants.API_KEY_ID, SM_API_KEY);

            if (SM_CLIENT_CERT_PASSWORD != null)

                env.put(Constants.CLIENT_CERT_PASSWORD_ID, SM_CLIENT_CERT_PASSWORD);

            if (SM_CLIENT_CERT_FILE != null)

                env.put(Constants.CLIENT_CERT_FILE_ID, SM_CLIENT_CERT_FILE);

            if (SM_HOST != null)

                env.put(Constants.HOST_ID, SM_HOST);

            env.put("PATH", System.getenv("PATH") + ":/" + dir + "/smtools-linux-x64/");

            processBuilder.directory(new File(dir));

            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            String line;

            while ((line = reader.readLine()) != null) {

                this.listener.getLogger().println(line);

            }

            int exitCode = process.waitFor();

            reader.close();

            return exitCode;

        } catch (IOException e) {

            e.printStackTrace(this.listener.error(e.getMessage()));

            return 1;

        } catch (InterruptedException e) {

            e.printStackTrace(this.listener.error(e.getMessage()));

            return 1;

        } catch (Exception e) {

            e.printStackTrace(this.listener.error(e.getMessage()));

            return 1;

        }

    }

    public void deleteFiles() {

        File directory = new File("/" + System.getProperty("user.name") + "/.gnupg");

        File[] files = directory.listFiles();

        if (files == null)
            return;
        for (File f : files) {

            if (f.getName().contains("kbx")) {

                if (f.delete())
                    this.listener.getLogger().println("\nDeleted file: " + f.getName() + "\n");
                else
                    this.listener.getLogger().println("\nFailed to delete file: " + f.getName() + "\n");
            }

        }

    }

    public Integer call(String os) throws IOException {

        result = install(os);

        if (result == 0)

            this.listener.getLogger().println("\nClient Tools Installation Complete\n");

        else {

            this.listener.getLogger().println("\nClient Tools Installation Failed\n");

            return result;

        }

        this.listener.getLogger().println("\nInstalling and configuring GPG Tool Suite\n");

        String userName = System.getProperty("user.name");

        if (userName.equals("root")) {
            result = executeCommand("apt-get --yes --assume-yes install gnupg");
        } else {
            result = executeCommand("sudo apt-get --yes --assume-yes install gnupg");
        }

        if (result == 0)

            this.listener.getLogger().println("\nGPG Installation Complete\n");

        else {

            this.listener.getLogger().println("\nGPG Installation Failed\n");

            return result;

        }

        result = executeCommand("gpgconf --kill all");

        this.listener.getLogger().println("\nCreating GPG Config File\n");

        String str = "verbose\ndebug-all\n" +

                "log-file /" + System.getProperty("user.name") + "/.gnupg/gpg-agent.log\n" +

                "scdaemon-program " + dir + "/ssm-scd\n";

        String configPath = "/" + System.getProperty("user.name") + "/.gnupg/gpg-agent.conf";

        result = createFile(configPath, str);

        if (result == 0)

            this.listener.getLogger()
                    .println("\nGPG config file successfully created at location: " + configPath + "\n");

        else {

            this.listener.getLogger().println("\nFailed to create GPG config file\n");

            return result;

        }

        setEnvVar("PATH", this.pathVar + ":/" + dir);

        executeCommand("gpgconf --kill all");

        result = executeCommand("gpg --card-status");

        deleteFiles();

        return result;

    }

}
