package experiments;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import experiments.GitHubItem.GitHubDirectory;
import experiments.GitHubItem.GitHubFile;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GitHubFile.class, name = "file"),
    @JsonSubTypes.Type(value = GitHubDirectory.class, name = "dir")
})
public sealed abstract class GitHubItem permits GitHubFile, GitHubDirectory {
  public String path;
  public String type;

  public static final class GitHubFile extends GitHubItem {

  }

  public static final class GitHubDirectory extends GitHubItem {

  }
}