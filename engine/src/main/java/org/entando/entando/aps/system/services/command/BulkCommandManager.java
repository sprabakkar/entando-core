package org.entando.entando.aps.system.services.command;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.entando.entando.aps.system.common.command.BaseBulkCommand;
import org.entando.entando.aps.system.common.command.report.BulkCommandReport;
import org.entando.entando.aps.system.common.command.thread.ApsCommandThread;
import org.entando.entando.aps.system.services.command.util.BulkCommandContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.agiletec.aps.system.common.AbstractService;
import com.agiletec.aps.util.DateConverter;

/**
 * Service responsible for the execution of commands.
 * 
 * @author E.Mezzano
 *
 */
public class BulkCommandManager extends AbstractService implements IBulkCommandManager, Runnable {

	private static final Logger _logger = LoggerFactory.getLogger(BulkCommandManager.class);

	@Override
	public void init() throws Exception {
		this.initScheduler();
		_logger.debug("{} ready", this.getClass().getName());
	}

	@Override
	public void destroy() {
		super.destroy();
		this.stopCommands();
		this.stopScheduler();
	}

	@Override
	protected void release() {
		super.release();
		this.stopScheduler();
	}

	protected void initScheduler() {
		// TODO Inizializzare scheduler che cerca i processi in esecuzione e libera la memoria
		
	}

	protected void stopScheduler() {
		// TODO
		
	}

	@Override
	public <I> BulkCommandReport<I> addCommand(String owner, BaseBulkCommand<I, ?> command) {
		return this.addCommand(owner, command, null);
	}

	@Override
	public <I> BulkCommandReport<I> addCommand(String owner, BaseBulkCommand<I, ?> command, Boolean execInThread) {
		String commandId = this.generateId();
		command.setId(commandId);
		this.runCommand(command, execInThread);
		this.putCommandInMap(owner, command);
		return command.getReport();
	}

	@Override
	public BaseBulkCommand<?, ?> getCommand(String owner, String commandId) {
		BulkCommandContainer container = this.getCommandContainer(owner, commandId);
		BaseBulkCommand<?, ?> command = container != null ? container.getCommand() : null;
		return command;
	}

	@Override
	public BulkCommandReport<?> getCommandReport(String owner, String commandId) {
		BaseBulkCommand<?, ?> command = this.getCommand(owner, commandId);
		BulkCommandReport<?> report = command != null ? command.getReport() : null;
		return report;
	}

	protected BulkCommandContainer getCommandContainer(String owner, String commandId) {
		BulkCommandContainer container = null;
		Map<String, BulkCommandContainer> commandsByOwner = this._commands.get(owner);
		if (commandsByOwner != null) {
			container = commandsByOwner.get(commandId);
		}
		return container;
	}

	protected void runCommand(BaseBulkCommand<?, ?> command, Boolean execInThread) {
		if (this.isToExecInThread(command, execInThread)) {
			ApsCommandThread thread = new ApsCommandThread(command);
			thread.start();
		} else {
			command.apply();
		}
	}

	protected boolean isToExecInThread(BaseBulkCommand<?, ?> command, Boolean execInThread) {
		boolean inThread = false;
		if (execInThread == null) {
			inThread = command.getItems().size() > 10;
		} else {
			inThread = execInThread;
		}
		return inThread;
	}

	protected synchronized void putCommandInMap(String owner, BaseBulkCommand<?, ?> command) {
		BulkCommandContainer container = new BulkCommandContainer();
		container.setOwner(owner);
		container.setCommand(command);
		
		Map<String, BulkCommandContainer> commandsByOwner = this._commands.get(owner);
		if (commandsByOwner == null) {
			commandsByOwner = new HashMap<String, BulkCommandContainer>();
			this._commands.put(owner, commandsByOwner);
		}
		commandsByOwner.put(command.getId(), container);
	}

	protected String generateId() {
		StringBuilder str = new StringBuilder("cmd_");
		str.append(DateConverter.getFormattedDate(new Date(), "ddMMyyyymmssSSS"));
		synchronized(this) {
			this._sec = (this._sec++) % 10;
			str.append(this._sec);
		}
		return str.toString();
	}

	protected synchronized void cleanCommands() {
		for (Map<String, BulkCommandContainer> commandsByOwner : this._commands.values()) {
			for (BulkCommandContainer container : commandsByOwner.values()) {
				if (this.isCommandExpired(container.getCommand())) {
					commandsByOwner.remove(container.getCommand().getId());
					if (commandsByOwner.isEmpty()) {
						String owner = container.getOwner();
						this._commands.remove(owner);
					}
				}
			}
		}
	}

	protected synchronized void stopCommands() {
		for (Map<String, BulkCommandContainer> commandsByOwner : this._commands.values()) {
			for (BulkCommandContainer container : commandsByOwner.values()) {
				BaseBulkCommand<?, ?> command = container.getCommand();
				if (!command.isEnded()) {
					command.stopCommand();
				}
			}
		}
	}

	protected boolean isCommandExpired(BaseBulkCommand<?, ?> command) {
		boolean expired = false;
		if (command.isEnded()) {
			Date endingDate = command.getEndingTime();
			if (endingDate != null) {
				long endingTime = (new Date()).getTime() - endingDate.getTime();
				expired = endingTime > CACHE_TIME;
			}
		}
		return expired;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		
	}

	protected Map<String, Map<String, BulkCommandContainer>> getCommands() {
		return this._commands;
	}

	protected int _sec = 0;
	private Map<String, Map<String, BulkCommandContainer>> _commands = new HashMap<String, Map<String, BulkCommandContainer>>();
	private static final long CACHE_TIME = 3600000;// 1 h

}
