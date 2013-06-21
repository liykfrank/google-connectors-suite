package org.mule.module.google.calendar.automation.testcases;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Map;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mule.api.MuleEvent;
import org.mule.api.processor.MessageProcessor;
import org.mule.module.google.calendar.model.AclRule;
import org.mule.module.google.calendar.model.Calendar;
import org.mule.module.google.calendar.model.Event;

public class InsertAclRuleTestCases  extends GoogleCalendarTestParent {
	
	@Before
	public void setUp() {
		try {
			testObjects = (Map<String, Object>) context.getBean("insertAclRule");
			
			// Insert calendar and get reference to retrieved calendar
			Calendar calendar = insertCalendar((Calendar) testObjects.get("calendarRef"));
			
			// Replace old calendar instance with new instance
			testObjects.put("calendarRef", calendar);
			testObjects.put("calendarId", calendar.getId());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}
	
	@Category({SmokeTests.class, SanityTests.class})	
	@Test
	public void testInsertAclRule(){
		try {
			
			MessageProcessor flow = lookupFlowConstruct("insert-acl-rule");
			MuleEvent event = flow.process(getTestEvent(testObjects));
			
			AclRule returnedAclRule = (AclRule) event.getMessage().getPayload();
			String ruleId = returnedAclRule.getId();
		
			testObjects.put("ruleId", ruleId);
			
			flow = lookupFlowConstruct("get-acl-rule-by-id");
			event = flow.process(getTestEvent(testObjects));
			
			AclRule afterProc = (AclRule) event.getMessage().getPayload();
			String ruleIdAfter = afterProc.getId();
			
			assertEquals(ruleId,ruleIdAfter);
			assertTrue(EqualsBuilder.reflectionEquals(returnedAclRule, afterProc));
		}
		catch (Exception e) {
			e.printStackTrace();
			fail();
		}		
	}
		
	@After
	public void tearDown() {
		try {
			String calendarId = testObjects.get("calendarId").toString();
			deleteCalendar(calendarId);
		}
		catch (Exception e) {
			e.printStackTrace();
			fail();
		}
	}

}
