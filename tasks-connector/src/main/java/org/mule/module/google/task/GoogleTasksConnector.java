/**
* Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
* The software in this package is published under the terms of the CPAL v1.0
* license, a copy of which has been included with this distribution in the
* LICENSE.txt file.
**/

/**
 * This file was automatically generated by the Mule Development Kit
 */
package org.mule.module.google.task;

import java.io.IOException;
import java.util.List;

import javax.inject.Inject;

import org.mule.api.MuleMessage;
import org.mule.api.annotations.Configurable;
import org.mule.api.annotations.Connector;
import org.mule.api.annotations.Processor;
import org.mule.api.annotations.oauth.OAuth2;
import org.mule.api.annotations.oauth.OAuthAccessToken;
import org.mule.api.annotations.oauth.OAuthAccessTokenIdentifier;
import org.mule.api.annotations.oauth.OAuthAuthorizationParameter;
import org.mule.api.annotations.oauth.OAuthConsumerKey;
import org.mule.api.annotations.oauth.OAuthConsumerSecret;
import org.mule.api.annotations.oauth.OAuthInvalidateAccessTokenOn;
import org.mule.api.annotations.oauth.OAuthPostAuthorization;
import org.mule.api.annotations.oauth.OAuthProtected;
import org.mule.api.annotations.oauth.OAuthScope;
import org.mule.api.annotations.param.Default;
import org.mule.api.annotations.param.Optional;
import org.mule.module.google.task.model.Task;
import org.mule.module.google.task.model.TaskList;
import org.mule.modules.google.AbstractGoogleOAuthConnector;
import org.mule.modules.google.AccessType;
import org.mule.modules.google.ForcePrompt;
import org.mule.modules.google.IdentifierPolicy;
import org.mule.modules.google.api.pagination.PaginationUtils;
import org.mule.modules.google.oauth.invalidation.InvalidationAwareCredential;
import org.mule.modules.google.oauth.invalidation.OAuthTokenExpiredException;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.tasks.Tasks.TasksOperations.Move;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.TaskLists;

/**
 * Google Tasks Cloud connector.
 * This connector covers almost all the Google Tasks API v3 using OAuth2 for authentication.
 *
 * @author MuleSoft, Inc.
 * @author mariano.gonzalez@mulesoft.com
 */
@Connector(name="google-tasks", schemaVersion="1.0", friendlyName="Google Tasks", minMuleVersion="3.4", configElementName="config-with-oauth")
@OAuth2(
		authorizationUrl = "https://accounts.google.com/o/oauth2/auth",
		accessTokenUrl = "https://accounts.google.com/o/oauth2/token",
		accessTokenRegex="\"access_token\"[ ]*:[ ]*\"([^\\\"]*)\"",
		expirationRegex="\"expires_in\"[ ]*:[ ]*([\\d]*)",
		refreshTokenRegex="\"refresh_token\"[ ]*:[ ]*\"([^\\\"]*)\"",
		authorizationParameters={
				@OAuthAuthorizationParameter(name="access_type", defaultValue="online", type=AccessType.class, description="Indicates if your application needs to access a Google API when the user is not present at the browser. " + 
											" Use offline to get a refresh token and use that when the user is not at the browser. Default is online", optional=true),
				@OAuthAuthorizationParameter(name="force_prompt", defaultValue="auto", type=ForcePrompt.class, description="Indicates if google should remember that an app has been authorized or if each should ask authorization every time. " + 
											" Use force to request authorization every time or auto to only do it the first time. Default is auto", optional=true)
		}
)
public class GoogleTasksConnector extends AbstractGoogleOAuthConnector {

	public static final String NEXT_PAGE_TOKEN = "GoogleTask_NEXT_PAGE_TOKEN";
	
	/**
     * The OAuth2 consumer key 
     */
    @Configurable
    @OAuthConsumerKey
    private String consumerKey;

    /**
     * The OAuth2 consumer secret 
     */
    @Configurable
    @OAuthConsumerSecret
    private String consumerSecret;
    
    /**
     * The OAuth scopes you want to request
     */
    @OAuthScope
    @Configurable
    @Optional
    @Default(USER_PROFILE_SCOPE + " " + TasksScopes.TASKS)
    private String scope;
    
    /**
     * This policy represents which id we want to use to represent each google account.
     * 
     * PROFILE means that we want the google profile id. That means, the user's primary key in google's DB.
     * This is a long number represented as a string.
     * 
     * EMAIL means you want to use the account's email address
     */
    @Configurable
    @Optional
    @Default("EMAIL")
    private IdentifierPolicy identifierPolicy = IdentifierPolicy.EMAIL;
    
    /**
     * Application name registered on Google API console
     */
    @Configurable
    @Optional
    @Default("Mule-GoogleTasksConnector/1.0")
    private String applicationName;
    
    @OAuthAccessToken
    private String accessToken;
    
	/**
	 * The google api client
	 */
	private com.google.api.services.tasks.Tasks client;
	
	@OAuthAccessTokenIdentifier
	public String getAccessTokenId() {
		return this.identifierPolicy.getId(this);
	}
	
	@OAuthPostAuthorization
	public void postAuth() {
		Credential credential = new InvalidationAwareCredential(BearerToken.authorizationHeaderAccessMethod());
		credential.setAccessToken(this.getAccessToken());
		
		this.client = new com.google.api.services.tasks.Tasks.Builder(new NetHttpTransport(), new JacksonFactory(), credential)
						.setApplicationName(this.applicationName)
						.build();
	}
	
    /**
     * Returns all the authenticated user's task lists.
     * 
     * For supporting google's paging mechanism, the next page token is store on the message property
     * &quot;GoogleTask_NEXT_PAGE_TOKEN&quot;. If there isn't a next page, then the property is removed
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:get-task-lists}
     * 
     * @param message the current mule message
     * @param maxResults Maximum number of task lists returned on one page
     * @param pageToken Token specifying the result page to return
     * @return a list with instances of {@link org.mule.module.google.task.model.TaskList}
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
	@Inject
    public List<TaskList> getTaskLists(
    		MuleMessage message,
    		@Optional @Default("100") long maxResults,
    		@Optional String pageToken) throws IOException {
    	
    	TaskLists list = this.client.tasklists().list()
			    			.setMaxResults(maxResults)
			    			.setPageToken(pageToken)
			    			.execute();
    	
    	PaginationUtils.savePageToken(NEXT_PAGE_TOKEN, list.getNextPageToken(), message);
    	return TaskList.valueOf(list.getItems(), TaskList.class);
    }
    
    /**
     * Returns the authenticated user's specified task list
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:get-task-list-by-id}
     * 
     * @param taskListId Task list identifier.
     * @return an instance of {@link org.mule.module.google.task.model.TaskList}
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public TaskList getTaskListById(@Optional @Default("@default") String taskListId) throws IOException {
    	return new TaskList(this.client.tasklists().get(taskListId).execute());
    }
    	
    /**
     * Creates a new task list and adds it to the authenticated user's task lists.
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:insert-task-list}
     * 
     * @param taskList the taskList to be inserted
     * @return an instance of {@link org.mule.module.google.task.model.TaskList} representing the newly inserted task list
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public TaskList insertTaskList(@Optional @Default("#[payload:]") TaskList taskList) throws IOException {
    	return new TaskList(this.client.tasklists().insert(taskList.wrapped()).execute());
    }
    
    /**
     * Updates the authenticated user's specified task list.
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:update-task-list}
     * 
     * @param taskList an instance of {@link org.mule.module.google.task.model.TaskList} with the task list's new state
     * @param taskListId task list identifier
     * @return an instance of {@link org.mule.module.google.task.model.TaskList} representing the state of the updated list
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public TaskList updateTaskList(
    							   @Optional @Default("#[payload:]") TaskList taskList,
    							   @Optional @Default("@default") String taskListId) throws IOException {
    	
    	return new TaskList(this.client.tasklists().update(taskListId, taskList.wrapped()).execute());
    }
    
    /**
     * Deletes the authenticated user's specified task list.
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:delete-task-list}
     * 
     * @param taskListId Task list identifier.
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public void deleteTaskList(@Optional @Default("@default") String taskListId) throws IOException {
    	this.client.tasklists().delete(taskListId).execute();
    }
    
    /**
     * Returns all tasks in the specified task list. This method accepts a number
     * of filtering attributes. The one for which no values is specified will not be used
     * when filtering
     * 
     * For supporting google's paging mechanism, the next page token is store on the message property
     * &quot;GoogleTask_NEXT_PAGE_TOKEN&quot;. If there isn't a next page, then the property is removed
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:get-tasks}
     * 
     * @param message the current mule message
     * @param taskListId Task list identifier
     * @param completedMin Lower bound for a task's completion date (as a RFC 3339 timestamp) to filter by
     * @param completedMax Upper bound for a task's completion date (as a RFC 3339 timestamp) to filter by
     * @param dueMin Lower bound for a task's due date (as a RFC 3339 timestamp) to filter by
     * @param dueMax Upper bound for a task's due date (as a RFC 3339 timestamp) to filter by
     * @param updatedMin Lower bound for a task's last modification time (as a RFC 3339 timestamp) to filter by
     * @param maxResults Maximum number of task lists returned on one page
     * @param pageToken Token specifying the result page to return
     * @param showDeleted Flag indicating whether deleted tasks are returned in the result
     * @param showHidden Flag indicating whether hidden tasks are returned in the result
     * @param showcompleted Flag indicating whether completed tasks are returned in the result
     * @return list with instances of {@link org.mule.module.google.task.model.Task}
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
	@Inject
    public List<Task> getTasks(
    					MuleMessage message,
    					@Optional @Default("@default") String taskListId,
    					@Optional String completedMin,
    					@Optional String completedMax,
    					@Optional String dueMin,
    					@Optional String dueMax,
    					@Optional String updatedMin,
    					@Optional @Default("100") long maxResults,
    					@Optional String pageToken,
    					@Optional @Default("false") boolean showDeleted,
    					@Optional @Default("false") boolean showHidden,
    					@Optional @Default("false") boolean showcompleted
    					) throws IOException {
    	
    	com.google.api.services.tasks.model.Tasks taskList = this.client.tasks().list(taskListId)
    			.setCompletedMax(completedMax)
    			.setCompletedMin(completedMin)
    			.setDueMin(dueMin)
    			.setDueMax(dueMax)
    			.setUpdatedMin(updatedMin)
    			.setMaxResults(maxResults)
    			.setPageToken(pageToken)
    			.setShowCompleted(showcompleted)
    			.setShowHidden(showHidden)
    			.setShowDeleted(showDeleted)
    			.execute();
    	
    	PaginationUtils.savePageToken(NEXT_PAGE_TOKEN, taskList.getNextPageToken(), message);
    	return Task.valueOf(taskList.getItems(), Task.class);
    }
    
    /**
     * Returns the specified task.
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:get-task-by-id}
     * 
     * @param taskListId Task list identifier
     * @param taskId Task identifier
     * @return an instance of {@link org.mule.module.google.task.model.Task}
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public Task getTaskById(@Optional @Default("@default") String taskListId, String taskId) throws IOException {
    	return new Task(this.client.tasks().get(taskListId, taskId).execute());
    }
    
    /**
     * Creates a new task on the specified task list
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:insert-task}
     * 
     * @param taskListId Task list identifier
     * @param task instance of {@link org.mule.module.google.task.model.Task} containing the state of the task to be inserted
     * @return instance of {@link org.mule.module.google.task.model.Task} representing the state of the newly inserted task
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public Task insertTask(@Optional @Default("@default") String taskListId, @Optional @Default("#[payload:]") Task task) throws IOException {
    	return new Task(this.client.tasks().insert(taskListId, task.wrapped()).execute());
    }
    
    /**
     * Updates the specified task.
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:update-task}
     * 
     * @param taskListId Task list identifier
     * @param taskId Task identifier.
     * @param task instance of {@link org.mule.module.google.task.model.Task} containing the state we want the updated task to have
     * @return an instance of {@link org.mule.module.google.task.model.Task} representing the state of the updated task
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public Task updateTask(@Optional @Default("@default") String taskListId, String taskId, @Optional @Default("#[payload:]") Task task) throws IOException {
    	return new Task(this.client.tasks().update(taskListId, taskId, task.wrapped()).execute());
    }
    
    /**
     * Deletes the specified task from the task list.
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:delete-task}
     * 
     * @param taskListId Task list identifier
     * @param taskId Task identifier.
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public void deleteTask(@Optional @Default("@default") String taskListId, String taskId) throws IOException {
    	this.client.tasks().delete(taskListId, taskId).execute();
    }
    
    /**
     * Moves the specified task to another position in the task list. This can include putting it as a
     * child task under a new parent and/or move it to a different position among its sibling tasks.
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:move}
     * 
     * @param taskListId Task list identifier
     * @param taskId identifier for the task being moved.
     * @param parentId Parent task identifier. If the task is created at the top level, this parameter is omitted
     * @param previousId New previous sibling task identifier. If the task is moved to the first position among its
     * 					 siblings, this parameter is omitted
     * @return an instance of {@link org.mule.module.google.task.model.Task} representing the state of the moved task
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public Task move(
    			@Optional @Default("@default") String taskListId,
    			String taskId,
    			@Optional String parentId,
    			@Optional String previousId) throws IOException {
    	
    	Move move = this.client.tasks().move(taskListId, taskId);
    	
    	if (parentId != null) {
    		move.setParent(parentId);
    	}
    	
    	if (previousId != null) {
    		move.setPrevious(previousId);
    	}
    	
    	return new Task(move.execute());
    }
    
    /**
     * Clears all completed tasks from the specified task list. The affected tasks will be marked as
     * 'hidden' and no longer be returned by default when retrieving all tasks for a task list.
     * 
     * {@sample.xml ../../../doc/GoogleTasksConnector.xml.sample google-tasks:clear-tasks}
     * 
     * @param taskListId Task list identifier
     * @throws IOException if there's an error in the communication
     */
    @Processor
	@OAuthProtected
	@OAuthInvalidateAccessTokenOn(exception=OAuthTokenExpiredException.class)
    public void clearTasks(@Optional @Default("@default") String taskListId) throws IOException {
    	this.client.tasks().clear(taskListId).execute();
    }
    
    
	public String getConsumerKey() {
		return consumerKey;
	}

	public void setConsumerKey(String consumerKey) {
		this.consumerKey = consumerKey;
	}

	public String getConsumerSecret() {
		return consumerSecret;
	}

	public void setConsumerSecret(String consumerSecret) {
		this.consumerSecret = consumerSecret;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getApplicationName() {
		return applicationName;
	}

	public void setApplicationName(String applicationName) {
		this.applicationName = applicationName;
	}

	@Override
	public String getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(String accessToken) {
		this.accessToken = accessToken;
	}

	public IdentifierPolicy getIdentifierPolicy() {
		return identifierPolicy;
	}

	public void setIdentifierPolicy(IdentifierPolicy identifierPolicy) {
		this.identifierPolicy = identifierPolicy;
	}
	
}
