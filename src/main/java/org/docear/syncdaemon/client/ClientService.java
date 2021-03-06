package org.docear.syncdaemon.client;

import akka.actor.ActorRef;
import org.docear.syncdaemon.fileindex.FileMetaData;
import org.docear.syncdaemon.projects.Project;
import org.docear.syncdaemon.users.User;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;

public interface ClientService {
    /**
     *
     */
    UploadResponse upload(User user, Project project, FileMetaData fileMetaData) throws FileNotFoundException;

    FileMetaData createFolder(User user, Project project, FileMetaData fileMetaData) throws FileNotFoundException;

    /**
     * @param user
     * @param project
     * @param fileMetaData
     * @return Most current FileMetaData of file
     */
    FileMetaData delete(User user, Project project, FileMetaData fileMetaData);

    InputStream download(User user, FileMetaData currentServerMetaData);

    ProjectResponse getProjects(User user);

    Project getProject(User user, String projectId);

    FolderMetaData getFolderMetaData(User user, FileMetaData folderMetaData);

    ListenForUpdatesResponse listenForUpdates(User user, Map<String, Long> projectIdRevisionMap, ActorRef actorRef);

    /**
     * @return {@link FileMetaData} or <code>null</code> if not present
     */
    FileMetaData getCurrentFileMetaData(User user, FileMetaData fileMetaData);

    DeltaResponse delta(User user, String projectId, Long sinceRevision);
}
