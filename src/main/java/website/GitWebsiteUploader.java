package website;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

public class GitWebsiteUploader implements WebsiteUploader {
    public static final String DAILY_FILES = "daily";
    private File dailyFiles;
    private Git mainRepo;
    private Git cacheRepo;
    private String branch;

    public GitWebsiteUploader(Git mainRepo, Git cacheRepo, String branch) {
        this.mainRepo = mainRepo;
        this.cacheRepo = cacheRepo;
        this.branch = branch;
    }

    @Override
    public void setPathToDaily(File dailyFiles) {
        this.dailyFiles = dailyFiles;
    }

    @Override
    public void update() {
        if (cacheRepo != null) {
            if (cacheRepo.exists()) {
                cacheRepo.pull();
            } else {
                mainRepo.clone(cacheRepo.getBasePath(), branch);
            }
        }

        try (Git tempRepo = cacheRepo != null ?
                cacheRepo :
                mainRepo.clone(tempDir("git-website-uploader-repo"), branch)) {
            Path start = dailyFiles.toPath();
            Files.walkFileTree(start, new CopyAndAddToGit(tempRepo, start));
            if (tempRepo.checkHasChanges()) {
                tempRepo.commit("Updating " + new Date());
                tempRepo.push();
            }
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private File tempDir(String prefix) throws IOException {
        return Files.createTempDirectory(prefix).toFile();
    }

    private static class CopyAndAddToGit extends SimpleFileVisitor<Path> {
        private final Git tempRepo;
        private final Path start;

        public CopyAndAddToGit(Git tempRepo, Path start) {
            this.tempRepo = tempRepo;
            this.start = start;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            Path inGitRepo = Paths.get(tempRepo.getBasePath(), DAILY_FILES)
                    .resolve(start.relativize(file));

            Files.createDirectories(inGitRepo.getParent());

            Files.copy(file, inGitRepo, StandardCopyOption.REPLACE_EXISTING);

            tempRepo.add(inGitRepo.toFile());

            return super.visitFile(file, attrs);
        }
    }
}
