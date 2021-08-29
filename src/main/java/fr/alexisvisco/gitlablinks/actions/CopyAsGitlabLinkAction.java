package fr.alexisvisco.gitlablinks.actions;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.TextTransferable;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class CopyAsGitlabLinkAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Editor editor = e.getData(PlatformDataKeys.EDITOR);
        if (editor == null)
            return;

        Project project = editor.getProject();
        if (project == null || project.getBasePath() == null) return;

        Optional<String> formatLineUrl = getUrlLineSelection(editor);
        if (formatLineUrl.isEmpty()) return;

        VirtualFile currentFile = FileDocumentManager.getInstance().getFile(editor.getDocument());
        if (currentFile == null) return;

        Optional<String> firstRemoteGitlabUrl = getCurrentGitUrl(project, currentFile);
        if (firstRemoteGitlabUrl.isEmpty()) return;

        Optional<String> branch = getBranch(project, currentFile);
        if (branch.isEmpty()) return;

        var filePath = getFilePath(project, currentFile);
        if (filePath.isEmpty()) return;


        var link = Paths.get(sshToHttps(
                        firstRemoteGitlabUrl.get()),
                "-", "tree", branch.get(),
                filePath.get()
        ) + formatLineUrl.get();

        System.out.println(currentFile.getPath());
        System.out.println(project.getBasePath());

        CopyPasteManager.getInstance().setContents(new TextTransferable(link));
        try {
            URL url = new URL(link);
            var b = WebBrowserManager.getInstance().getFirstActiveBrowser();
            if (b != null) {
                BrowserUtil.browse(url);
            }
        } catch (MalformedURLException ignored) {
        }
    }

    private Optional<String> getFilePath(Project project, VirtualFile currentFile) {
        var mod = ModuleUtil.findModuleForFile(currentFile, project);
        if (mod == null) {
            return Optional.of(currentFile.getPath().replace(project.getBasePath(), ""));
        }

        // got bla/bla/module.iml: removing the module.iml
        String[] pathElements = mod.getModuleFilePath().split("/");
        var finalPath = Arrays.stream(pathElements).limit(pathElements.length - 1).collect(Collectors.joining("/"));

        // currentFile.getPath() = directory_path/thing/file.ext: removing directory_path
        return Optional.of(currentFile.getPath().replace(finalPath, ""));
    }

    private Optional<String> getUrlLineSelection(Editor editor) {
        var selModel = editor.getSelectionModel();

        var lineStart = 0;
        Optional<Integer> lineEnd = Optional.empty();

        var vpStart = selModel.getSelectionStartPosition();
        if (vpStart == null) {
            lineStart = editor.getCaretModel().getPrimaryCaret().getVisualLineStart();
        } else {
            lineStart = vpStart.getLine();
        }

        var vpEnd = selModel.getSelectionEndPosition();
        if (vpEnd != null && vpEnd.getLine() != lineStart) {
            lineEnd = Optional.of(vpEnd.getLine());
        }

        var formatLineUrl = String.format("#L%d", lineStart + 1) +
                (lineEnd.isPresent()
                        ? String.format("-L%d", lineEnd.get() + 1)
                        : "");

        return Optional.of(formatLineUrl);
    }


    private Optional<String> getCurrentGitUrl(Project pro, VirtualFile currentFile) {
        GitRepository vcs = GitRepositoryManager.getInstance(pro).getRepositoryForFileQuick(currentFile);
        if (vcs == null) return Optional.empty();

        return vcs.getRemotes()
                .stream()
                .map(r -> (r.getUrls()
                                .stream()
                                .filter(u -> u.contains("gitlab"))
                                .collect(Collectors.toList())
                        )
                ).flatMap(Collection::stream).findFirst();
    }

    private Optional<String> getBranch(Project pro, VirtualFile currentFile) {
        GitRepository vcs = GitRepositoryManager.getInstance(pro).getRepositoryForFileQuick(currentFile);
        if (vcs == null || vcs.getCurrentBranch() == null)
            return Optional.empty();

        return Optional.of(vcs.getCurrentBranch().getName());
    }

    /**
     * @return git@gitlab.com:miimosa/go/libcommon.git -> https://gitlab.com/miimosa/go/libcommon
     */
    private String sshToHttps(String str) {
        return str
                .replace(":", "/")
                .replace("git@", "https://")
                .replace(".git", "");
    }
}
