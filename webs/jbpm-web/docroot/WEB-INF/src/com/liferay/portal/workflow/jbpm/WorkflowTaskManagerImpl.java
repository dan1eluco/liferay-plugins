/**
 * Copyright (c) 2000-2009 Liferay, Inc. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.liferay.portal.workflow.jbpm;

import com.liferay.portal.kernel.util.ArrayUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.OrderByComparator;
import com.liferay.portal.kernel.workflow.WorkflowException;
import com.liferay.portal.kernel.workflow.WorkflowTask;
import com.liferay.portal.kernel.workflow.WorkflowTaskManager;
import com.liferay.portal.service.RoleLocalServiceUtil;
import com.liferay.portal.workflow.jbpm.dao.CustomSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.db.TaskMgmtSession;
import org.jbpm.graph.def.Transition;
import org.jbpm.graph.node.TaskNode;
import org.jbpm.taskmgmt.def.Task;
import org.jbpm.taskmgmt.exe.PooledActor;
import org.jbpm.taskmgmt.exe.TaskInstance;

/**
 * <a href="WorkflowTaskManagerImpl.java.html"><b><i>View Source</i></b></a>
 *
 * @author Shuyang Zhou
 * @author Brian Wing Shun Chan
 */
public class WorkflowTaskManagerImpl implements WorkflowTaskManager {

	public WorkflowTask assignWorkflowTaskToRole(
			long userId, long workflowTaskId, long roleId, String comment,
			Map<String, Object> context)
		throws WorkflowException {

		JbpmContext jbpmContext = _jbpmConfiguration.createJbpmContext();

		try {
			TaskMgmtSession taskMgmtSession = jbpmContext.getTaskMgmtSession();

			TaskInstance taskInstance = taskMgmtSession.loadTaskInstance(
				workflowTaskId);

			taskInstance.setPooledActors(String.valueOf(roleId));

			taskInstance.addComment(comment);

			if (context != null) {
				taskInstance.addVariables(context);
			}

			jbpmContext.save(taskInstance);

			return new WorkflowTaskImpl(taskInstance);
		}
		catch (Exception e) {
			throw new WorkflowException(e);
		}
		finally {
			jbpmContext.close();
		}
	}

	public WorkflowTask assignWorkflowTaskToUser(
			long userId, long workflowTaskId, long assigneeUserId,
			String comment, Map<String, Object> context)
		throws WorkflowException {

		JbpmContext jbpmContext = _jbpmConfiguration.createJbpmContext();

		try {
			TaskMgmtSession taskMgmtSession = jbpmContext.getTaskMgmtSession();

			TaskInstance taskInstance = taskMgmtSession.loadTaskInstance(
				workflowTaskId);

			Set<PooledActor> pooledActors = taskInstance.getPooledActors();

			if ((pooledActors == null) || pooledActors.isEmpty()) {
				throw new WorkflowException(
					"Workflow task " + workflowTaskId +
						" has not been assigned to a role");
			}

			PooledActor pooledActor = pooledActors.iterator().next();

			long roleId = GetterUtil.getLong(pooledActor.getActorId());

			if (!RoleLocalServiceUtil.hasUserRole(assigneeUserId, roleId)) {
				throw new WorkflowException(
					"Workflow task " + workflowTaskId +
						" cannot be assigned to user " + assigneeUserId);
			}

			taskInstance.setActorId(String.valueOf(assigneeUserId));

			taskInstance.addComment(comment);

			if (context != null) {
				taskInstance.addVariables(context);
			}

			jbpmContext.save(taskInstance);

			return new WorkflowTaskImpl(taskInstance);
		}
		catch (Exception e) {
			throw new WorkflowException(e);
		}
		finally {
			jbpmContext.close();
		}
	}

	public WorkflowTask completeWorkflowTask(
			long userId, long workflowTaskId, String transitionName,
			String comment, Map<String, Object> context)
		throws WorkflowException {

		JbpmContext jbpmContext = _jbpmConfiguration.createJbpmContext();

		try {
			TaskMgmtSession taskMgmtSession = jbpmContext.getTaskMgmtSession();

			TaskInstance taskInstance = taskMgmtSession.loadTaskInstance(
				workflowTaskId);

			long actorId = GetterUtil.getLong(taskInstance.getActorId());

			if (actorId != userId) {
				throw new WorkflowException(
					"Workflow task " + workflowTaskId +
						" is not assigned to user " + userId);
			}

			taskInstance.addComment(comment);

			if (context != null) {
				taskInstance.addVariables(context);
			}

			if (transitionName == null) {
				taskInstance.end();
			}
			else {
				taskInstance.end(transitionName);
			}

			jbpmContext.save(taskInstance);

			return new WorkflowTaskImpl(taskInstance);
		}
		catch (Exception e) {
			throw new WorkflowException(e);
		}
		finally {
			jbpmContext.close();
		}
	}

	public List<String> getNextTransitionNames(long userId, long workflowTaskId)
		throws WorkflowException {

		JbpmContext jbpmContext = _jbpmConfiguration.createJbpmContext();

		try {
			TaskMgmtSession taskMgmtSession = jbpmContext.getTaskMgmtSession();

			TaskInstance taskInstance = taskMgmtSession.loadTaskInstance(
				workflowTaskId);

			long actorId = GetterUtil.getLong(taskInstance.getActorId());

			if (actorId != userId) {
				throw new WorkflowException(
					"Workflow task " + workflowTaskId +
						" is not assigned to user " + userId);
			}

			Task task = taskInstance.getTask();
			TaskNode taskNode = task.getTaskNode();
			List<Transition> transitions = taskNode.getLeavingTransitions();

			List<String> transitionNames = new ArrayList<String>(
				transitions.size());

			for (Transition transition : transitions) {
				transitionNames.add(transition.getName());
			}

			return transitionNames;
		}
		catch (Exception e) {
			throw new WorkflowException(e);
		}
		finally {
			jbpmContext.close();
		}
	}

	public WorkflowTask getWorkflowTask(long workflowTaskId)
			throws WorkflowException {
		JbpmContext jbpmContext = _jbpmConfiguration.createJbpmContext();

		try {
			TaskMgmtSession taskMgmtSession = jbpmContext.getTaskMgmtSession();

			TaskInstance taskInstance = taskMgmtSession.loadTaskInstance(
				workflowTaskId);

			return new WorkflowTaskImpl(taskInstance);
		}
		catch (Exception e) {
			throw new WorkflowException(e);
		}
		finally {
			jbpmContext.close();
		}
	}

	public int getWorkflowTaskCountByRole(long roleId, Boolean completed)
		throws WorkflowException {

		return getWorkflowTaskCount(new long[] {roleId}, true, completed);
	}

	public int getWorkflowTaskCountByUser(long userId, Boolean completed)
		throws WorkflowException {

		return getWorkflowTaskCount(new long[] {userId}, false, completed);
	}

	public int getWorkflowTaskCountByWorkflowInstance(
			long workflowInstanceId, Boolean completed)
		throws WorkflowException {

		JbpmContext jbpmContext = _jbpmConfiguration.createJbpmContext();

		try {
			CustomSession customSession = new CustomSession(jbpmContext);

			return customSession.countTaskInstances(
				-1, workflowInstanceId, null, false, completed);
		}
		catch (Exception e) {
			throw new WorkflowException(e);
		}
		finally {
			jbpmContext.close();
		}
	}

	public List<WorkflowTask> getWorkflowTasksByRole(
			long roleId, Boolean completed, int start, int end,
			OrderByComparator orderByComparator)
		throws WorkflowException {

		return getWorkflowTasks(
			-1, new long[] {roleId}, true, completed, start, end,
			orderByComparator);
	}

	public List<WorkflowTask> getWorkflowTasksByUser(
			long userId, Boolean completed, int start, int end,
			OrderByComparator orderByComparator)
		throws WorkflowException {

		return getWorkflowTasks(
			-1, new long[] {userId}, false, completed, start, end,
			orderByComparator);
	}

	public List<WorkflowTask> getWorkflowTasksByWorkflowInstance(
			long workflowInstanceId, Boolean completed, int start, int end,
			OrderByComparator orderByComparator)
		throws WorkflowException {

		return getWorkflowTasks(
			workflowInstanceId, null, false, completed, start, end,
			orderByComparator);
	}

	public void setJbpmConfiguration(JbpmConfiguration jbpmConfiguration) {
		_jbpmConfiguration = jbpmConfiguration;
	}

	protected int getWorkflowTaskCount(
			long[] actorIds, boolean pooledActors, Boolean completed)
		throws WorkflowException {

		JbpmContext jbpmContext = _jbpmConfiguration.createJbpmContext();

		try {
			CustomSession customSession = new CustomSession(jbpmContext);

			return customSession.countTaskInstances(
				-1, -1, ArrayUtil.toStringArray(actorIds), pooledActors,
				completed);
		}
		catch (Exception e) {
			throw new WorkflowException(e);
		}
		finally {
			jbpmContext.close();
		}
	}

	protected List<WorkflowTask> getWorkflowTasks(
			long workflowInstanceId, long[] actorIds, boolean pooledActors,
			Boolean completed, int start, int end,
			OrderByComparator orderByComparator)
		throws WorkflowException {

		JbpmContext jbpmContext = _jbpmConfiguration.createJbpmContext();

		try {
			CustomSession customSession = new CustomSession(jbpmContext);

			String[] actorIdStringArray = null;

			if (actorIds != null) {
				actorIdStringArray = ArrayUtil.toStringArray(actorIds);
			}

			List<TaskInstance> taskInstances = customSession.findTaskInstances(
				-1, workflowInstanceId, actorIdStringArray, pooledActors,
				completed, start, end, orderByComparator);

			return toWorkflowTasks(taskInstances);
		}
		catch (Exception e) {
			throw new WorkflowException(e);
		}
		finally {
			jbpmContext.close();
		}
	}

	protected List<WorkflowTask> toWorkflowTasks(
		List<TaskInstance> taskInstances) {

		List<WorkflowTask> taskInstanceInfos =
			new ArrayList<WorkflowTask>(taskInstances.size());

		for (TaskInstance taskInstance : taskInstances) {
			taskInstanceInfos.add(new WorkflowTaskImpl(taskInstance));
		}

		return taskInstanceInfos;
	}

	private JbpmConfiguration _jbpmConfiguration;

}