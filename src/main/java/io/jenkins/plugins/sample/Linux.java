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



public class Linux  {

    private final TaskListener listener;

    private final String SM_HOST;

    private final String SM_API_KEY;

    private final String SM_CLIENT_CERT_FILE;

    private final String SM_CLIENT_CERT_PASSWORD;

    private final String pathVar;

    private final String prompt = "bash";

    private final char c = '-';

    String dir = System.getProperty("user.dir");

    private Integer result;

    ProcessBuilder processBuilder = new ProcessBuilder();



    public Linux(TaskListener listener,  String SM_HOST, String SM_API_KEY, String SM_CLIENT_CERT_FILE, String SM_CLIENT_CERT_PASSWORD, String pathVar) {

        this.listener = listener;

        this.SM_HOST = SM_HOST;

        this.SM_API_KEY = SM_API_KEY;

        this.SM_CLIENT_CERT_FILE = SM_CLIENT_CERT_FILE;

        this.SM_CLIENT_CERT_PASSWORD = SM_CLIENT_CERT_PASSWORD;

        this.pathVar = pathVar;

    }



    public Integer install(String os) {

        this.listener.getLogger().println("\nAgent type: "+os);

        this.listener.getLogger().println("\nIstalling SMCTL\n");

        executeCommand("curl -X GET https://stage.one.digicert.com/signingmanager/api-ui/v1/releases/noauth/smtools-linux-x64.tar.gz/download/ -o smtools-linux-x64.tar.gz");

        result = executeCommand("tar xvf smtools-linux-x64.tar.gz > /dev/null");

        if (result==0)

            this.listener.getLogger().println("\nSMCTL Istallation Complete\n");

        else {

            this.listener.getLogger().println("\nSMCTL Istallation Failed\n");

            return result;

        }

        dir = dir+File.separator+"smtools-linux-x64";

        this.listener.getLogger().println("\nInstalling SCD\n");

        result = executeCommand("curl -X GET https://stage.one.digicert.com/signingmanager/api-ui/v1/releases/noauth/ssm-scd-linux-x64/download -o ssm-scd");

        if (result==0)

            this.listener.getLogger().println("\nSCD Istallation Complete\n");

        else {

            this.listener.getLogger().println("\nSCD Istallation Failed\n");

        }

        result = executeCommand("sudo chmod -R +x "+dir);

        return result;

//        this.listener.getLogger().println("Verifying Installation\n");

//        executeCommand("./smctl keypair ls > /dev/null");

//        this.listener.getLogger().println("Installation Verification Complete\n");

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



        try {

            processBuilder.command(prompt,c+"c",command);

            Map<String, String> env = processBuilder.environment();

            // if(SM_API_KEY!=null)

            //     env.put("SM_API_KEY", SM_API_KEY);

            // if(SM_CLIENT_CERT_PASSWORD!=null)

            //     env.put("SM_CLIENT_CERT_PASSWORD", SM_CLIENT_CERT_PASSWORD);

            // if(SM_CLIENT_CERT_FILE!=null)

            //     env.put("SM_CLIENT_CERT_FILE", SM_CLIENT_CERT_FILE);

            // if(SM_HOST!=null)

            //     env.put("SM_HOST", SM_HOST);

            // env.put("PATH",System.getenv("PATH")+":/"+dir+"/smtools-linux-x64/");

            processBuilder.directory(new File(dir));

            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();



            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));



            String line;



            while ((line = reader.readLine()) != null) {

                this.listener.getLogger().println(line);

            }

            int exitCode = process.waitFor();

            return exitCode;

//            try {

//                if (exitCode != 0) throw new Exception("Command failed");

//            }

//            catch (Exception e) {

//                e.printStackTrace(this.listener.error(e.getMessage()));

//            }

        } catch (IOException e) {

            e.printStackTrace(this.listener.error(e.getMessage()));

            return 1;

        } catch (InterruptedException e) {

            e.printStackTrace(this.listener.error(e.getMessage()));

            return 1;

        }

        catch (Exception e) {

            e.printStackTrace(this.listener.error(e.getMessage()));

            return 1;

        }

    }



    public void deleteFiles(){

        File directory = new File("/"+System.getProperty("user.name")+"/.gnupg");

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

        this.listener.getLogger().println("\nInstalling and configuring GPG Tool Suite\n");

        result = executeCommand("sudo apt-get install gnupg");

        if (result==0)

            this.listener.getLogger().println("\nGPG Istallation Complete\n");

        else {

            this.listener.getLogger().println("\nGPG Istallation Failed\n");

            return result;

        }

        this.listener.getLogger().println("\nCreating GPG Config File\n");
       

        String str = "verbose\ndebug-all\n" +

                "log-file /"+System.getProperty("user.name")+"/.gnupg/gpg-agent.log\n" +

                "scdaemon-program "+dir+"/ssm-scd\n";

        String configPath = "/"+System.getProperty("user.name")+"/.gnupg/gpg-agent.conf";



        result = createFile(configPath, str);



        if (result==0)

            this.listener.getLogger().println("\nGPG config file successfully created at location: "+configPath+"\n");

        else {

            this.listener.getLogger().println("\nFailed to create GPG config file\n");

            return result;

        }

        setEnvVar("PATH",this.pathVar+":/"+dir);

        executeCommand("gpgconf --kill all");

        result = executeCommand("gpg --card-status");

        deleteFiles();

        return result;

    }

}

