package net.minecraftforge.gitver;

public record GitInfo(String tag, String offset, String hash, String branch, String commit, String abbreviatedId) {
    public static final GitInfo EMPTY = new GitInfo(
        "0.0", "0", "00000000", "master", "0000000000000000000000", "00000000"
    );

    static final class Builder {
        String tag;
        String offset;
        String hash;
        String branch;
        String commit;
        String abbreviatedId;

        public GitInfo build() {
            return new GitInfo(tag, offset, hash, branch, commit, abbreviatedId);
        }
    }
}
