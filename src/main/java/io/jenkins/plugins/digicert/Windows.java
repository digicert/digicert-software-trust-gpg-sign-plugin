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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;

public class Windows {
    private final TaskListener listener;
    private final String SM_HOST;
    // lgtm[jenkins/plaintext-storage]
    private final String SM_API_KEY;
    private final String SM_CLIENT_CERT_FILE;
    // lgtm[jenkins/plaintext-storage]
    private final String SM_CLIENT_CERT_PASSWORD;
    private final String pathVar;
    private final String prompt = "cmd.exe";
    private final char c = '/';
    String dir = System.getProperty("user.dir");
    ProcessBuilder processBuilder = new ProcessBuilder();
    private Integer result;

    public Windows(TaskListener listener, String SM_HOST, String SM_API_KEY, String SM_CLIENT_CERT_FILE,
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
        this.listener.getLogger()
                .println("\nInstalling SMCTL from: https://" + SM_HOST.substring(19).replaceAll("/$", "")
                        + "/signingmanager/api-ui/v1/releases/noauth/smtools-windows-x64.msi/download \n");
        executeCommand("curl -X GET  https://" + SM_HOST.substring(19).replaceAll("/$", "")
                + "/signingmanager/api-ui/v1/releases/noauth/smtools-windows-x64.msi/download -o smtools-windows-x64.msi");
        result = executeCommand("msiexec /i smtools-windows-x64.msi /quiet /qn");
        if (result == 0)
            this.listener.getLogger().println("\nSMCTL Installation Complete\n");
        else {
            this.listener.getLogger().println("\nSMCTL Installation Failed\n");
            return result;
        }
        File destFile = new File(dir + "\\ssm-scd.exe");
        if (destFile.exists()) {
            if (!destFile.delete())
                return 1;
        }
        this.listener.getLogger().println("\nInstalling SCD from: https://" + SM_HOST.substring(19).replaceAll("/$", "")
                + "/signingmanager/api-ui/v1/releases/noauth/ssm-scd-windows-x64/download \n");
        executeCommand("curl -X GET  https://" + SM_HOST.substring(19).replaceAll("/$", "")
                + "/signingmanager/api-ui/v1/releases/noauth/ssm-scd-windows-x64/download -o ssm-scd.exe");
        result = moveFile();

        if (SM_API_KEY != null && SM_CLIENT_CERT_FILE != null && SM_CLIENT_CERT_PASSWORD != null) {
            executeCommand(
                    "C:\\Windows\\System32\\certutil.exe -csp \"DigiCert Signing Manager KSP\" -key -user > NUL 2> NUL");
            executeCommand("smksp_cert_sync.exe > NUL 2> NUL");
            executeCommand("smctl windows certsync > NUL 2> NUL");
        }

        return result;
    }

    public Integer moveFile() {
        try {
            String scdPath;
            try (InputStream input = Windows.class.getResourceAsStream("config.properties")) {
                Properties prop = new Properties();
                prop.load(input);
                scdPath = prop.getProperty("scdPath");
            } catch (IOException e) {
                e.printStackTrace(this.listener.error(e.getMessage()));
                return 1;
            }
            File destFile = new File(scdPath);
            if (destFile.exists()) {
                this.listener.getLogger().println("\nSCD already exists, replacing with newer version\n");
                if (destFile.delete())
                    this.listener.getLogger().println("\nSCD replaced with newer version\n");
                ;
            }
            if (destFile.exists()) {
                this.listener.getLogger().println("\nSCD could not be replaced because it is open in another program," +
                        " using the existing version\n");
                return 0;
            }
            Path temp = Files.move(Paths.get(dir + "\\ssm-scd.exe"),
                    Paths.get(scdPath));
            if (temp != null) {
                this.listener.getLogger().println("\nSCD Installation Complete\n");
                return 0;
            } else {
                this.listener.getLogger().println("\nSCD Installation Failed\n");
                return 1;
            }
        } catch (IOException e) {
            e.printStackTrace(this.listener.error(e.getMessage()));
            return 1;
        }
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
                        + ". This is due to the plugin running on a slave node. This will have to be manually applied.");
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
        int exitCode;
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
            env.put("path", System.getenv("path")
                    + ";C:\\Program Files\\DigiCert\\DigiCert One Signing Manager Tools;C:\\Program Files (x86)\\GnuPG\\bin");
            processBuilder.directory(new File(dir));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

            String line;

            while ((line = reader.readLine()) != null) {
                this.listener.getLogger().println(line);
            }
            exitCode = process.waitFor();
            reader.close();
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
        return exitCode;
    }

    public Integer gpgInstall() {
        try {
            this.listener.getLogger().println("\nInstalling and configuring GPG Tool Suite\n");
            String gpgUrl;
            try (InputStream input = Windows.class.getResourceAsStream("config.properties")) {

                Properties prop = new Properties();
                prop.load(input);
                gpgUrl = prop.getProperty("gpgUrl");
                // this.listener.getLogger().println(prop.getProperty("gpgUrl"));
            } catch (IOException e) {
                e.printStackTrace(this.listener.error(e.getMessage()));
                return 1;
            }
            executeCommand("curl -X GET " + gpgUrl + " -o gpg4win.exe");
            result = executeCommand("gpg4win.exe /S");
            return result;
        } catch (Exception e) {
            e.printStackTrace(this.listener.error(e.getMessage()));
            return 1;
        }
    }

    public void deleteFiles() {
        File directory = new File("C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Roaming\\gnupg");
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
        result = gpgInstall();
        if (result == 0)
            this.listener.getLogger().println("\nGPG Installation Complete\n");
        else {
            this.listener.getLogger().println("\nGPG Installation Failed\n");
            return result;
        }
        this.listener.getLogger().println("\nCreating GPG Config File\n");
        String str = "verbose\ndebug-all\n" +
                "log-file C:\\Users\\" + System.getProperty("user.name") + "\\AppData\\Roaming\\gnupg\\gpg-agent.log\n"
                +
                "scdaemon-program \"C:\\Program Files\\DigiCert\\DigiCert One Signing Manager Tools\\ssm-scd.exe\"\n";

        String configPath = "C:\\Users\\" + System.getProperty("user.name")
                + "\\AppData\\Roaming\\gnupg\\gpg-agent.conf";

        result = createFile(configPath, str);

        if (result == 0)
            this.listener.getLogger()
                    .println("\nGPG config file successfully created at location: " + configPath + "\n");
        else {
            this.listener.getLogger().println("\nFailed to create GPG config file\n");
            return result;
        }
        setEnvVar("PATH", this.pathVar + ";" + dir
                + ";C:\\Program Files\\DigiCert\\DigiCert One Signing Manager Tools;C:\\Program Files (x86)\\GnuPG\\bin");
        executeCommand("gpgconf --kill all");
        result = executeCommand("gpg --card-status");
        deleteFiles();
        executeCommand("set " + "path=%path%;" + dir + ";" +
                " & smctl windows certsync > NUL 2> NUL");
        return result;
    }
}
