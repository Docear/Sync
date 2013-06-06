package org.docear.syncdaemon.fileindex;

import java.util.List;

import org.docear.syncdaemon.indexdb.IndexDbService;
import org.docear.syncdaemon.indexdb.IndexDbServiceImpl;
import org.docear.syncdaemon.messages.FileChangeEvent;
import org.docear.syncdaemon.messages.FileConflictEvent;
import org.docear.syncdaemon.projects.Project;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;

public class FileIndexServiceImpl extends UntypedActor implements
		FileIndexService {

	private ActorRef recipient;
	private Project project;
	private final IndexDbService indexDbService;

	public FileIndexServiceImpl() {
		indexDbService = new IndexDbServiceImpl();
	}

	public void onReceive(Object message) throws Exception {
		if (message instanceof StartScanMessage) {
			this.project = ((StartScanMessage) message).getProject();
			this.recipient = ((StartScanMessage) message).getServerSynchronisationActor();
			scanProject();
		}
	}

	@Override
	public void scanProject() {
		long localRev = indexDbService.getProjectRevision(project.getId());

		if (localRev != project.getRevision()) {
			List<FileMetaData> files = FileReceiver.receiveFiles(project);

			// TODO user credentials required to use clientService
			for (FileMetaData fmdFromScan : files) {
				final FileMetaData fmdFromIndexDb = indexDbService.getFileMetaData(fmdFromScan);
				if (fmdFromScan.isChanged(fmdFromIndexDb)) {
					sendConflictMesage(fmdFromScan);
				} else {
					sendFileChangedMessage(fmdFromScan);
				}
			}
		}
	}

	private void sendConflictMesage(final FileMetaData fmd) {
		final FileConflictEvent message = new FileConflictEvent(fmd.getPath(),project.getId());
		recipient.tell(message, recipient);
	}

	private void sendFileChangedMessage(final FileMetaData fmd) {
		final FileChangeEvent message = new FileChangeEvent(fmd.getPath(),project.getId());
		recipient.tell(message, recipient);
	}

}
