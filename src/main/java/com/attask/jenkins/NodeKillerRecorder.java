package com.attask.jenkins;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Node;
import hudson.slaves.OfflineCause;
import hudson.tasks.*;
import jenkins.model.Jenkins;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: brianmondido
 * Date: 8/22/12
 * Time: 3:55 PM
 */
public class NodeKillerRecorder extends Recorder {


    private Pattern nodePattern;
    private String emailAddress;
    public static Logger LOGGER = Logger.getLogger(NodeKillerRecorder.class.getSimpleName());

    @DataBoundConstructor
    public NodeKillerRecorder(String nodePattern, String emailAddress){
        this.nodePattern= Pattern.compile(nodePattern);
        this.emailAddress=emailAddress;

    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        Node builtOn = build.getBuiltOn();
        String nodeName = builtOn.toComputer().getDisplayName();
        Matcher matcher= getNodePattern().matcher(nodeName);

        if(matcher.matches()){
            LOGGER.log(Level.FINE,"Disconnecting node: "+build.getFullDisplayName());
            builtOn.toComputer().disconnect(OfflineCause.create(new Localizable(ResourceBundleHolder.get(this.getClass()),"Automatically disconnected")));
            try {
                emailToAddress(getEmailAddress(),build);
            } catch (MessagingException e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }

    private void emailToAddress(String emailAddress, AbstractBuild<?,?> build) throws MessagingException {
        LOGGER.log(Level.FINE,"Sending email!");
        String url = Jenkins.getInstance().getRootUrl() + build.getUrl();
        MimeMessage msg=new MimeMessage(Mailer.descriptor().createSession());
        msg.setFrom(new InternetAddress(Mailer.descriptor().getAdminAddress()));
        String message = MessageFormat.format("The build, {0} has disconnected a node on Jenkins.<br/>Click here to see the killer build!<br/><a href={1}\">{2}</a>", build.getFullDisplayName(), url, build.getFullDisplayName());
        msg.setContent(message, "text/html");
        msg.setSentDate(new Date());
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(emailAddress));
        msg.setSubject("Build: " + build.getId()+" killed a node");

        Transport.send(msg);
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    public Pattern getNodePattern() {
        return nodePattern;
    }

    public String getEmailAddress() {
        return emailAddress;
    }


    @Extension
    public static class BuildStepDescriptorImpl extends BuildStepDescriptor<Publisher> {
        public BuildStepDescriptorImpl() {
            super(NodeKillerRecorder.class);
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Kill Node";
        }
    }
}
