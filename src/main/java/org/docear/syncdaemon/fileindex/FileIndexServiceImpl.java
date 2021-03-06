package org.docear.syncdaemon.fileindex;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import net.contentobjects.jnotify.JNotify;
import net.contentobjects.jnotify.JNotifyException;
import org.docear.syncdaemon.fileactors.Messages.FileChangedLocally;
import org.docear.syncdaemon.indexdb.IndexDbService;
import org.docear.syncdaemon.indexdb.PersistenceException;
import org.docear.syncdaemon.jnotify.Listener;
import org.docear.syncdaemon.projects.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FileIndexServiceImpl extends UntypedActor implements
        FileIndexService {
    private static final Logger logger = LoggerFactory.getLogger(FileIndexServiceImpl.class);

    private ActorRef recipient;
    private Project project;
    private IndexDbService indexDbService;
    private int jNotifyWatchId;


    public FileIndexServiceImpl(IndexDbService indexDbService) {
        this.indexDbService = indexDbService;
    }

    public void onReceive(Object message) throws Exception {
        if (message instanceof StartScanMessage) {
            this.project = ((StartScanMessage) message).getProject();
            this.recipient = ((StartScanMessage) message).getFileChangeActor();

            //look if project exists in db
            try {
                indexDbService.getProjectRevision(project.getId());
            } catch (Exception e) {
                //not present
                indexDbService.setProjectRevision(project.getId(),project.getRevision());
            }
            scanProject();
        }
    }

    @Override
    public void scanProject() {
        try {
            long localRev = indexDbService.getProjectRevision(project.getId());

            if (localRev != project.getRevision()) {
                List<FileMetaData> fmdsFromScan = FileReceiver.receiveFiles(project);
                List<FileMetaData> fmdsFromIndexDb = indexDbService.getFileMetaDatas(project.getId());

                for (FileMetaData fmdFromScan : fmdsFromScan) {
                	FileMetaData match = null;
                	for (FileMetaData fmdFromIndexDb: fmdsFromIndexDb){
                		if (fmdFromScan.getPath().equals(fmdFromIndexDb.getPath())){
                			match = fmdFromIndexDb;
                			break;
                		}
                	}
                
                	if (match == null || fmdFromScan.isChanged(match)) {
                		sendFileChangedMessage(fmdFromScan.getPath());
                	}
                	
                	if (match != null){
                		fmdsFromIndexDb.remove(match);
                    }
                }
                
                for (FileMetaData fmdFromIndexDb : fmdsFromIndexDb) {
                	sendFileChangedMessage(fmdFromIndexDb.getPath());
                }
            }
        } catch (PersistenceException e) {
            logger.error("can't scan projects", e);
        }
        final Listener listener = new Listener(project,recipient);
        try {
            jNotifyWatchId = JNotify.addWatch(project.getRootPath(),JNotify.FILE_ANY,true,listener);
        } catch (JNotifyException e) {
            try {
                JNotify.removeWatch(jNotifyWatchId);
            } catch (JNotifyException e1) {
            }
        }
    }

    private void sendFileChangedMessage(final String path) {
    	final FileChangedLocally message = new FileChangedLocally(this.project, path);
        recipient.tell(message, recipient);
    }

}