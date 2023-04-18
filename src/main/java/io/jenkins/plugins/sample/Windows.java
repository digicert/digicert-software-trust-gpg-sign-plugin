package io.jenkins.plugins.sample;

import hudson.EnvVars;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;
import jenkins.model.Jenkins;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.*;
import java.io.*;
import java.net.URI;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.net.URL;

import org.apache.commons.httpclient.HttpException;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import hudson.model.TaskListener;
import jenkins.security.MasterToSlaveCallable;

public class Windows {
    private final TaskListener listener;
    private final String SM_HOST;
    private final String SM_API_KEY;
    private final String SM_CLIENT_CERT_FILE;
    private final String SM_CLIENT_CERT_PASSWORD;
    private final String pathVar;
    private final String prompt = "cmd.exe";
    private final char c = '/';
    String dir = System.getProperty("user.dir");
    private Integer result;
    ProcessBuilder processBuilder = new ProcessBuilder();

    public Windows(TaskListener listener, String SM_HOST, String SM_API_KEY, String SM_CLIENT_CERT_FILE, String SM_CLIENT_CERT_PASSWORD, String pathVar) {
        this.listener = listener;
        this.SM_HOST = SM_HOST;
        this.SM_API_KEY = SM_API_KEY;
        this.SM_CLIENT_CERT_FILE = SM_CLIENT_CERT_FILE;
        this.SM_CLIENT_CERT_PASSWORD = SM_CLIENT_CERT_PASSWORD;
        this.pathVar = pathVar;
    }

    public Integer install(String os) {
        this.listener.getLogger().println("\nAgent type: " + os);
        this.listener.getLogger().println("\nInstalling SMCTL\n");
        executeCommand("curl -X GET  https://stage.one.digicert.com/signingmanager/api-ui/v1/releases/noauth/smtools-windows-x64.msi/download -o smtools-windows-x64.msi");
        result = executeCommand("msiexec /i smtools-windows-x64.msi /quiet /qn");
        if (result==0)
            this.listener.getLogger().println("\nSMCTL Istallation Complete\n");
        else {
            this.listener.getLogger().println("\nSMCTL Istallation Failed\n");
            return result;
        }
        this.listener.getLogger().println("\nInstalling SCD\n");
        File destFile = new File(dir + "\\ssm-scd.exe");
        if(destFile.exists()) {
            destFile.delete();
        }
        executeCommand("curl -X GET https://stage.one.digicert.com/signingmanager/api-ui/v1/releases/noauth/ssm-scd-windows-x64/download -o ssm-scd.exe");
        result = moveFile();
        if (result==0)
            this.listener.getLogger().println("\nSCD Istallation Complete\n");
        else {
            this.listener.getLogger().println("\nSCD Istallation Failed\n");
        }
//        this.listener.getLogger().println("Verifying Installation\n");
//        executeCommand("smksp_registrar.exe list > NUL");
//        executeCommand("smctl.exe keypair ls > NUL");
        executeCommand("C:\\Windows\\System32\\certutil.exe -csp \"DigiCert Signing Manager KSP\" -key -user > NUL 2> NUL");
//        executeCommand("smksp_cert_sync.exe > NUL 2> NUL");
//        executeCommand("smctl windows certsync > NUL 2> NUL");
//        this.listener.getLogger().println("Installation Verification Complete\n");
        return result;
    }

    public Integer moveFile(){
        try {
            File destFile = new File("C:\\Program Files\\DigiCert\\DigiCert One Signing Manager Tools\\ssm-scd.exe");
            if(destFile.exists()) {
                destFile.delete();
            }
            Path temp = Files.move
                    (Paths.get(dir + "\\ssm-scd.exe"),
                            Paths.get("C:\\Program Files\\DigiCert\\DigiCert One Signing Manager Tools\\ssm-scd.exe"));
            if (temp != null) {
                this.listener.getLogger().println("\nSCD moved successfully\n");
                return 0;
            } else {
                this.listener.getLogger().println("\nFailed to move the SCD\n");
                return 1;
            }
        }
        catch (IOException e) {
            e.printStackTrace(this.listener.error(e.getMessage()));
            return 1;
        }
    }

    public Integer createFile(String path, String str) {

        File file = new File(path); //initialize File object and passing path as argument
        boolean result;
        try {
            result = file.createNewFile();  //creates a new file
            try {
                String name = file.getCanonicalPath();
                FileOutputStream fos = new FileOutputStream(name, false);  // true for append mode
                byte[] b = str.getBytes();       //converts string into bytes
                fos.write(b);           //writes bytes into file
                fos.close();            //close the file
                return 0;
            } catch (Exception e) {
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
            Jenkins instance = Jenkins.getInstance();

            DescribableList<NodeProperty<?>, NodePropertyDescriptor> globalNodeProperties = instance.getGlobalNodeProperties();
            List<EnvironmentVariablesNodeProperty> envVarsNodePropertyList = globalNodeProperties.getAll(EnvironmentVariablesNodeProperty.class);

            EnvironmentVariablesNodeProperty newEnvVarsNodeProperty = null;
            EnvVars envVars = null;

            if (envVarsNodePropertyList == null || envVarsNodePropertyList.size() == 0) {
                newEnvVarsNodeProperty = new hudson.slaves.EnvironmentVariablesNodeProperty();
                globalNodeProperties.add(newEnvVarsNodeProperty);
                envVars = newEnvVarsNodeProperty.getEnvVars();
            } else {
                //We do have a envVars List
                envVars = envVarsNodePropertyList.get(0).getEnvVars();
            }
            envVars.put(key, value);
            instance.save();
        }
        catch (IOException e) {
            e.printStackTrace(this.listener.error(e.getMessage()));
        }
    }

    public Integer executeCommand(String command) {
        int exitCode;
        try {
            processBuilder.command(prompt, c + "c", command);
            Map<String, String> env = processBuilder.environment();
            if(SM_API_KEY!=null)
            env.put("SM_API_KEY", SM_API_KEY);
            if(SM_CLIENT_CERT_PASSWORD!=null)
            env.put("SM_CLIENT_CERT_PASSWORD", SM_CLIENT_CERT_PASSWORD);
            if(SM_CLIENT_CERT_FILE!=null)
            env.put("SM_CLIENT_CERT_FILE", SM_CLIENT_CERT_FILE);
            if(SM_HOST!=null)
            env.put("SM_HOST", SM_HOST);
            env.put("path", System.getenv("path") + ";C:\\Program Files\\DigiCert\\DigiCert One Signing Manager Tools;C:\\Program Files (x86)\\GnuPG\\bin");
            processBuilder.directory(new File(dir));
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;

            while ((line = reader.readLine()) != null) {
                this.listener.getLogger().println(line);
            }
            exitCode = process.waitFor();
//            try {
//                if (exitCode != 0) throw new Exception("Command failed");
//            } catch (Exception e) {
//                e.printStackTrace(this.listener.error(e.getMessage()));
//            }
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

            executeCommand("curl -X GET https://files.gpg4win.org/gpg4win-4.1.0.exe -o gpg4win.exe");
            result = executeCommand("gpg4win.exe /S");
            return result;
        } catch (Exception e) {
            e.printStackTrace(this.listener.error(e.getMessage()));
            return 1;
        }
    }

    public void deleteFiles(){
        File directory = new File("C:\\Users\\"+System.getProperty("user.name")+"\\AppData\\Roaming\\gnupg");
        File[] files = directory.listFiles();
        for (File f : files)
        {
            if (f.getName().contains("kbx"))
            {
                f.delete();
                this.listener.getLogger().println("\nDeleted file: "+f.getName()+"\n");
            }
        }
    }

    public Integer call(String os) throws IOException {

        result = install(os);

        if (result==0)
            this.listener.getLogger().println("\nClient Tools Istallation Complete\n");
        else {
            this.listener.getLogger().println("\nClient Tools Istallation Failed\n");
            return result;
        }
        result = gpgInstall();
        if (result==0)
            this.listener.getLogger().println("\nGPG Istallation Complete\n");
        else {
            this.listener.getLogger().println("\nGPG Istallation Failed\n");
            return result;
        }
        this.listener.getLogger().println("\nCreating GPG Config File\n");
        String str = "verbose\ndebug-all\n" +
                "log-file C:\\Users\\"+System.getProperty("user.name")+"\\AppData\\Roaming\\gnupg\\gpg-agent.log\n" +
                "scdaemon-program \"C:\\Program Files\\DigiCert\\DigiCert One Signing Manager Tools\\ssm-scd.exe\"\n";

        String configPath = "C:\\Users\\"+System.getProperty("user.name")+"\\AppData\\Roaming\\gnupg\\gpg-agent.conf";

        result = createFile(configPath, str);

        if (result==0)
            this.listener.getLogger().println("\nGPG config file successfully created at location: "+configPath+"\n");
        else {
            this.listener.getLogger().println("\nFailed to create GPG config file\n");
            return result;
        }
        setEnvVar("PATH",this.pathVar+";"+dir+";C:\\Program Files\\DigiCert\\DigiCert One Signing Manager Tools;C:\\Program Files (x86)\\GnuPG\\bin");
        executeCommand("gpgconf --kill all");
        result = executeCommand("gpg --card-status");
        deleteFiles();
        executeCommand("set " + "path=%path%;" + dir + ";" +
                " & smctl windows certsync > NUL 2> NUL");
        return result;
    }
}

