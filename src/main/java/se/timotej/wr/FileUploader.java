package se.timotej.wr;

import com.jcraft.jsch.*;

import java.io.File;

public class FileUploader {
    public void upload(File file) throws JSchException, SftpException {
        ChannelSftp channelSftp = setupJsch();
        channelSftp.connect();

        String remoteDir = "wr/live/";

        channelSftp.put(file.getAbsolutePath(), remoteDir + file.getName());

        channelSftp.exit();
    }

    private ChannelSftp setupJsch() throws JSchException {
        JSch jsch = new JSch();
        jsch.setKnownHosts(System.getProperty("user.home")+"/.ssh/known_hosts");
        Session jschSession = jsch.getSession("timotej.se", "ssh.timotej.se");
        jschSession.setPassword(System.getProperty("ftpPassword"));
        jschSession.connect();
        return (ChannelSftp) jschSession.openChannel("sftp");
    }
}
