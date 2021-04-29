import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.NamespaceManager;
import com.google.appengine.api.ThreadManager;
import com.google.apphosting.api.ApiProxy;

public class ThreadingServlet extends HttpServlet {
	
	Logger logger = Logger.getLogger(ThreadingServlet.class.getName());
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
		if (Boolean.parseBoolean(req.getParameter("workaround"))) {
			reproduceEnvironmentReplacementProblem();
		} else {
			reproduceNamespaceProblem();
		}
	}
	
	private void reproduceNamespaceProblem() {
		NamespaceManager.set("namespace1");
		logger.info("Namespace on main thread: " + NamespaceManager.get());
		
		ThreadFactory threadFactory = ThreadManager.currentRequestThreadFactory();
		threadFactory.newThread(() -> {
			logger.info("Namespace on request thread: " + NamespaceManager.get());
			NamespaceManager.set("namespace2");
		}).start();
		
		waitForRequestThread();
		
		logger.info("Namespace on main thread after request thread changed it: " + NamespaceManager.get());
	}
	
	private void reproduceEnvironmentReplacementProblem() {
		ThreadFactory threadFactory = ThreadManager.currentRequestThreadFactory();
		ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
		
		Future<?> result = executor.submit(() -> {
			CopiedEnvironment copiedEnvironment = new CopiedEnvironment(ApiProxy.getCurrentEnvironment());
			ApiProxy.setEnvironmentForCurrentThread(copiedEnvironment);
			try {
				logger.severe("logging doesn't work");
			} catch (Exception e) {
				System.out.println("logging doesn't work");
				e.printStackTrace();
			}
		});
		
		try {
			logger.info("result: " + result.get());
		} catch (Exception e) {
			logger.log(Level.SEVERE, "task failed", e);
		}
		
		
		
	}
	
	private void waitForRequestThread() {
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			logger.severe("sleep interrupted");
		}
	}
	
	static class CopiedEnvironment implements ApiProxy.Environment {
		
		String appId;
		String moduleId;
		String versionId;
		String email;
		boolean loggedIn;
		boolean admin;
		String authDomain;
		String requestNamespace;
		Map<String, Object> attributes;
		Instant requestEnd;
		
		public CopiedEnvironment(ApiProxy.Environment other) {
			this.appId = other.getAppId();
			this.moduleId = other.getModuleId();
			this.versionId = other.getVersionId();
			this.email = other.getEmail();
			this.loggedIn = other.isLoggedIn();
			this.admin = other.isAdmin();
			this.authDomain = other.getAuthDomain();
			this.requestNamespace = other.getRequestNamespace();
			this.attributes = new HashMap<>(other.getAttributes());
			requestEnd = Instant.now().plus(Duration.ofMillis(other.getRemainingMillis()));
		}
		
		public long getRemainingMillis() {
			return requestEnd.toEpochMilli() - System.currentTimeMillis();
		}
		
		@Override
		public String getAppId() {
			return appId;
		}
		
		@Override
		public String getModuleId() {
			return moduleId;
		}
		
		@Override
		public String getVersionId() {
			return versionId;
		}
		
		@Override
		public String getEmail() {
			return email;
		}
		
		@Override
		public boolean isLoggedIn() {
			return loggedIn;
		}
		
		@Override
		public boolean isAdmin() {
			return admin;
		}
		
		@Override
		public String getAuthDomain() {
			return authDomain;
		}
		
		@Override
		public String getRequestNamespace() {
			return requestNamespace;
		}
		
		@Override
		public Map<String, Object> getAttributes() {
			return attributes;
		}
		
	}
}
