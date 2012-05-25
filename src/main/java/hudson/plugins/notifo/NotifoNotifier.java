package hudson.plugins.notifo;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import hudson.Extension;
import hudson.Launcher;

import hudson.console.ConsoleNote;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.Descriptor.FormException;
import hudson.model.Result;
import hudson.model.StreamBuildListener;
import hudson.model.User;

import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;

import net.sf.json.JSONObject;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;

public class NotifoNotifier extends Notifier {

    public final String serviceUser;
    public final String apiToken;
    public final String userNames;
    public final boolean notifyOnSuccess;
    public final boolean appendGlobalUserNames;
    private transient Notifo notifo;

    @DataBoundConstructor
    public NotifoNotifier(String serviceUser, String apiToken, String userNames, boolean notifyOnSuccess,
                          boolean appendGlobalUserNames) {
        this.serviceUser = serviceUser;
        this.apiToken = apiToken;
        this.userNames = userNames;
        this.notifyOnSuccess = notifyOnSuccess;
        this.appendGlobalUserNames = appendGlobalUserNames;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    private void initializeNotifo()
            throws IOException {
        if (notifo == null) {
            String serviceUser = this.serviceUser;
            String apiToken = this.apiToken;
            if ((serviceUser == null) || (serviceUser.trim().length() == 0)) {
              serviceUser = DESCRIPTOR.getServiceUser();
              /*
               * API Token is liken to the user so treat them as a couple
               */
              apiToken = DESCRIPTOR.apiToken;
            }
            Iterable<String> userNames = Splitter.on(',').omitEmptyStrings().trimResults().split(this.userNames);
            if (this.appendGlobalUserNames) {
              userNames = Iterables.concat(userNames, Splitter.on(',').omitEmptyStrings().trimResults().split(DESCRIPTOR.getUserNames()));
            }
            notifo = new Notifo(serviceUser,
                     apiToken,
                     userNames);
        }
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
            throws InterruptedException, IOException {
        if (!build.getResult().toString().equals(Result.SUCCESS.toString()) || notifyOnSuccess) {
            initializeNotifo();
            String message = build.getProject().getName() + ": " + build.getResult().toString() + "\n";
            if (!build.getCulprits().isEmpty()) {
                for (User user : build.getCulprits()) {
                    message = message + "Possible Culprit: " + user.getDisplayName();
                }
            }
            notifo.post(message, listener);
        }
        return true;
    }

    @Extension
    public static final DescriptorImpl DESCRIPTOR = new DescriptorImpl();
    
    public static final class DescriptorImpl
            extends BuildStepDescriptor<Publisher> {
        private String serviceUser;
        
        private String apiToken;
        
        private String userNames;
        
        public DescriptorImpl() {
          load();
        }
        /*
         * (non-Javadoc)
         *
         * @see hudson.tasks.BuildStepDescriptor#isApplicable(java.lang.Class)
         */

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        /*
         * (non-Javadoc)
         *
         * @see hudson.model.Descriptor#getDisplayName()
         */
        @Override
        public String getDisplayName() {
            return "Notifo";
        }
        
        public String getServiceUser() {
          return this.serviceUser;
        }
        
        public String getApiToken() {
          return this.apiToken;
        }
        
        public String getUserNames() {
          return this.userNames;
        }

        /**
         * {@inheritedDoc}
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject json)
            throws FormException {
          this.serviceUser = json.getString("serviceUser");
          this.apiToken = json.getString("apiToken");
          this.userNames = json.getString("userNames");
          save();
          return false;
        }
        
        public FormValidation doSendSampleNotification(@QueryParameter("serviceUser") final String serviceUser, @QueryParameter("apiToken") final String apiToken,
                                                       @QueryParameter("userNames") final String userNames) throws IOException, ServletException {
          try {
              Notifo notifo = new Notifo(serviceUser, apiToken, Splitter.on(',').omitEmptyStrings().trimResults().split(userNames));
              ByteArrayOutputStream bos = new ByteArrayOutputStream();
              /*
               * for whatever reason, Jenkins is storing some kind of encrypted exception stack trace through the annotate method. So I decided to override it as a no-op
               * as the content of the log is displayed by the user interface
               */
              BuildListener listener = new StreamBuildListener(bos) {
                @Override
                public void annotate(ConsoleNote ann) throws IOException {
                }
              };
              notifo.post("Sample notification", listener);
              if (bos.size() > 0) {
                return FormValidation.error("Notify returned following errors %s",new String(bos.toByteArray()));
              }
              return FormValidation.ok("Success");
          } catch (IOException e) {
              return FormValidation.error("Client error : "+e.getMessage());
          }
      }
        
        
    }
}
