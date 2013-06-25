package org.docear.syncdaemon.jnotify;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import net.contentobjects.jnotify.JNotifyListener;
import org.docear.syncdaemon.fileactors.Messages.FileChangedLocally;
import org.docear.syncdaemon.fileindex.FileMetaData;
import org.docear.syncdaemon.hashing.HashAlgorithm;
import org.docear.syncdaemon.hashing.SHA2;
import org.docear.syncdaemon.projects.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Listener implements JNotifyListener {
    private static final Logger logger = LoggerFactory.getLogger(Listener.class);
    private final ActorRef recipient;
    private final Project project;
    private final HashAlgorithm hashAlgorithm;
    private final ActorSystem system;
    private final Map<String,Cancellable> pathJobMap = new HashMap<String, Cancellable>();

    public Listener(Project project, ActorRef recipient) {
        this.recipient = recipient;
        this.system = ActorSystem.apply();

        this.project = project;
        this.hashAlgorithm = new SHA2();
    }

    @Override
    public void fileCreated(final int wd, final String rootPath, final String name) {
        logger.debug("fileCreated {}/{}", rootPath, name);
        final SendChangeRunnable sendChangeRunnable = new SendChangeRunnable(project,recipient,hashAlgorithm,rootPath,name,false);
        final Cancellable cancellable = system.scheduler().scheduleOnce(Duration.apply(1, TimeUnit.SECONDS), sendChangeRunnable,system.dispatcher());
        putInCancellableMap(rootPath,name,cancellable);
        //sendFileChangedMessage(createFileMetaData(rootPath, name, false));

    }

    @Override
    public void fileDeleted(final int wd, final String rootPath, final String name) {
        logger.debug("fileDeleted {}/{}", rootPath, name);
        final SendChangeRunnable sendChangeRunnable = new SendChangeRunnable(project,recipient,hashAlgorithm,rootPath,name,true);
        final Cancellable cancellable = system.scheduler().scheduleOnce(Duration.apply(1, TimeUnit.SECONDS), sendChangeRunnable,system.dispatcher());
        putInCancellableMap(rootPath,name,cancellable);
        //sendFileChangedMessage(createFileMetaData(rootPath, name, true));
    }

    @Override
    public void fileModified(final int wd, final String rootPath, final String name) {
        logger.debug("fileModified {}/{}", rootPath, name);
        final SendChangeRunnable sendChangeRunnable = new SendChangeRunnable(project,recipient,hashAlgorithm,rootPath,name,false);
        final Cancellable cancellable = system.scheduler().scheduleOnce(Duration.apply(1, TimeUnit.SECONDS), sendChangeRunnable,system.dispatcher());
        putInCancellableMap(rootPath,name,cancellable);
//        sendFileChangedMessage(createFileMetaData(rootPath, name, false));
    }

    @Override
    public void fileRenamed(final int wd, final String rootPath, final String oldName, final String newName) {
        logger.debug("fileRenamed rootpath={}, oldName={}, newName={}", rootPath, oldName, newName);
        final SendChangeRunnable sendChangeRunnable = new SendChangeRunnable(project,recipient,hashAlgorithm,rootPath,oldName,false);
        final Cancellable cancellable = system.scheduler().scheduleOnce(Duration.apply(1, TimeUnit.SECONDS), sendChangeRunnable,system.dispatcher());
        putInCancellableMap(rootPath,oldName,cancellable);
        final SendChangeRunnable sendChangeRunnable2 = new SendChangeRunnable(project,recipient,hashAlgorithm,rootPath,newName,false);
        final Cancellable cancellable2 = system.scheduler().scheduleOnce(Duration.apply(1, TimeUnit.SECONDS), sendChangeRunnable2,system.dispatcher());
        putInCancellableMap(rootPath,newName,cancellable2);
//        sendFileChangedMessage(createFileMetaData(rootPath, oldName, true));
//        sendFileChangedMessage(createFileMetaData(rootPath, newName, false));
    }

    public void putInCancellableMap(String rootPath, String path, Cancellable cancellable) {
        final String fullQualifier = rootPath+path;
        if(pathJobMap.containsKey(fullQualifier)) {
            final Cancellable oldCancellable = pathJobMap.get(fullQualifier);
            if(!oldCancellable.isCancelled())
                oldCancellable.cancel();
        }
        pathJobMap.put(fullQualifier,cancellable);
    }

//    private void sleep(long millis) {
//        try {
//            Thread.sleep(millis);
//        } catch (InterruptedException e) {
//        }
//    }



//    private void sendFileChangedMessage(final FileMetaData fileMetaData) {
//        final FileChangedLocally message = new FileChangedLocally(this.project, fileMetaData);
//
//        recipient.tell(message, recipient);
//    }


    private static class SendChangeRunnable implements Runnable {
        private final Project project;
        private final ActorRef recipient;
        private final HashAlgorithm hashAlgorithm;
        private final String rootPath;
        private final String filename;
        private final boolean isDeleted;

        private SendChangeRunnable(Project project, ActorRef recipient, HashAlgorithm hashAlgorithm, String rootPath, String filename, boolean deleted) {
            this.project = project;
            this.recipient = recipient;
            this.hashAlgorithm = hashAlgorithm;
            this.rootPath = rootPath;
            this.filename = filename;
            isDeleted = deleted;
        }


        @Override
        public void run() {

            final FileMetaData fileMetaData = createFileMetaData(rootPath,filename,isDeleted);
            logger.debug("scr => getting meta data: "+fileMetaData);
            final FileChangedLocally message = new FileChangedLocally(this.project, fileMetaData);
            logger.debug("scr => sending");
            recipient.tell(message, recipient);
        }

        private FileMetaData createFileMetaData(final String path, final String name, final boolean isDeleted) {
            File f = new File(path, name);


            boolean isDirectory = f.isDirectory();
            String hash = "";
            if (!isDeleted && !isDirectory) {

                //wait until file is accessible
//                OutputStream out = null;
//                while(out == null) {
//                    try {
//                        out = new FileOutputStream(f,true); // -> throws a FileNotFoundException
//                    } catch (FileNotFoundException e) {
//                        out = null;
//                        sleep(100);
//                    }
//                }
//                IOUtils.closeQuietly(out);

//                sleep(20);
                try {
                    while (hash.equals("")) {
                        try {
                            hash = hashAlgorithm.generate(f);
                        } catch (FileNotFoundException e) {
                            //do nothing
                        }
                    }
                } catch (IOException e) {
                    logger.error("Couldn't create Hash for FileMetaData for file \"" + name + "\".", e);
                }
            }
            FileMetaData fileMetaData = new FileMetaData(
                    "/" + name,
                    hash,
                    project.getId(),
                    isDirectory,
                    isDeleted,
                    -1);
            logger.debug(fileMetaData.toString());
            return fileMetaData;
        }
    }
}
