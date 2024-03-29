package org.testunited.sdk;

import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.TestExecutionResult.Status;
import org.junit.platform.engine.reporting.ReportEntry;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class TestUnitedTestExecutionListener implements TestExecutionListener {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	private static final String SESSION_NAME_KEY = "testunited.testsession.name";
	private static final String RESULT_SUBMISSION_ROUTE = "/testresultsubmissions";
	TestResultSubmission submission = new TestResultSubmission();
	private TestResultSubmissionSummary summary = new TestResultSubmissionSummary();
	List<TestResult> tests = new ArrayList<TestResult>();
	
	public TestResultSubmissionSummary getSummary() {
		return this.summary;
	}
	@Override
	public void testPlanExecutionStarted(TestPlan testPlan) {
		this.submission.setSessionName(System.getProperty(SESSION_NAME_KEY));
	}

	@Override
	public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
		if (testIdentifier.isTest()) {
			boolean isSuccessful = (testExecutionResult.getStatus() == Status.SUCCESSFUL);

			String reason = "";
			if (testExecutionResult.getThrowable().isPresent()) {
				reason = testExecutionResult.getThrowable().get().getMessage().replaceAll("\n", "").replaceAll("\'",
						"");
			}

			String suite = "";
			String separator = "class:";
			if (testIdentifier.getParentId().isPresent()) {
				suite = testIdentifier.getParentId().get();
				suite = suite.substring(suite.indexOf(separator) + separator.length(), suite.lastIndexOf("]"));
			}

			String name = testIdentifier.getDisplayName();
			if (name.indexOf("(") != -1) {
				name = name.substring(0, name.lastIndexOf("("));
			}

			TestResult testRun = new TestResult(String.format("%s.%s", suite, name), isSuccessful, reason);

			this.tests.add(testRun);
		}
	}

	@Override
	public void reportingEntryPublished(TestIdentifier testIdentifier, ReportEntry entry) {

		// TODO Auto-generated method stub
		TestExecutionListener.super.reportingEntryPublished(testIdentifier, entry);
	}

	@Override
	public void testPlanExecutionFinished(TestPlan testPlan) {

		String testunited_service_url = PropertyReader.getPropValue("TESTUNITED_SERVICE_URL");

		if (testunited_service_url == null || testunited_service_url.isEmpty())
			testunited_service_url = PropertyReader.getPropValue("testunited.service.url");

		if (testunited_service_url == null || testunited_service_url.isEmpty()) {
			logger.info("TestUnited endpoint is not provided, hence exiting.");
			return;
		}

		logger.debug("TESTUNITED_SERVICE_URL: {}", testunited_service_url);

		this.submission.setTestResults(this.tests);

		StringBuilder payloadBuilder = new StringBuilder();
		ObjectMapper mapper = new ObjectMapper();

		try {
			payloadBuilder.append(mapper.writeValueAsString(this.submission));
		} catch (JsonProcessingException e) {
			logger.info("JSON processing failed for the test results.");

			if (logger.isDebugEnabled()) {
				e.printStackTrace();
			}
		}

		HttpClient httpclient = HttpClients.createDefault();
		String payload = payloadBuilder.toString();
		StringEntity requestEntity = new StringEntity(payload, ContentType.APPLICATION_JSON);

		if (logger.isDebugEnabled()) {
			mapper.enable(SerializationFeature.INDENT_OUTPUT);
			mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

			try {
				String formattedPayload = mapper.writeValueAsString(this.submission);
				StringBuilder debugMsgBuilder = new StringBuilder();
				debugMsgBuilder.append("\n----------TESTUNITED PAYLOAD------------\n");
				debugMsgBuilder.append(formattedPayload);
				debugMsgBuilder.append("\n----------------------------------------\n");
				logger.debug(debugMsgBuilder.toString());

			} catch (JsonProcessingException e) {
				logger.debug("JSON processing failed for the test results.");
				e.printStackTrace();
			}
		}

		String testunited_endpoint = testunited_service_url + RESULT_SUBMISSION_ROUTE;
		HttpPost postMethod = new HttpPost(testunited_endpoint);
		postMethod.setEntity(requestEntity);
		HttpResponse rawResponse = null;

		try {
			logger.info("Posting test results to {}.", testunited_endpoint);
			rawResponse = httpclient.execute(postMethod);

			int http_status_expected = 201;

			if (rawResponse.getStatusLine().getStatusCode() == http_status_expected) {
				logger.info("SUCCESSFUL: Posting test results to {}.", testunited_endpoint);
			} else {
				logger.error("FAILED: Posting test results to {}. \n HTTP_STATUS:{}\n{}", testunited_endpoint,
						rawResponse.getStatusLine().getStatusCode(), rawResponse.getStatusLine().getReasonPhrase());
			}

			HttpEntity entity = rawResponse.getEntity();

			if (entity != null) {
				String result = EntityUtils.toString(entity);

				if(logger.isDebugEnabled()) {
					StringBuilder debugMsgBuilder = new StringBuilder();
					debugMsgBuilder.append("\n------------TEST SUBMISSION SUMMARY------------\n");
					debugMsgBuilder.append(result);
					debugMsgBuilder.append("\n-----------------------------------------------\n");
	
					logger.debug("------------TEST SUBMISSION SUMMARY------------");
				}
				
				ObjectMapper objectMapper = new ObjectMapper();
				
				this.summary = objectMapper.readValue(result, TestResultSubmissionSummary.class);

				logger.info("Submission Summary:{}", summary);
				EntityUtils.consume(entity);
			}

		} catch (Exception e) {
			logger.error("FAILED: Posting test results to {}.", testunited_endpoint);
			e.printStackTrace();
		} finally {
		}
	}
}
