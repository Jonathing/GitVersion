package net.minecraftforge.gitver;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class Main {
    public static void main(String[] args) throws Exception {
        // TODO [Utils] Move option parsing to a general utility library?
        var parser = Util.make(new OptionParser(), OptionParser::allowsUnrecognizedOptions);

        //@formatter:off
        // help message
        var help0 = parser.accepts("help",
            "Displays this help message and exits")
            .forHelp();

        var tagPrefix0 = parser.accepts("tag-prefix",
            "The prefix to use when filtering tags for this version (will always include a '-' at the end)")
            .withOptionalArg().ofType(String.class);

        // marker file
        var marker0 = parser.accepts("marker",
            "Marker file name(s) to indicate the root of projects (used to filter out subprojects from version)")
            .withRequiredArg().ofType(String.class);

        // ignore file
        var ignoreFile0 = parser.accepts("ignore",
            "Marker file name(s) to indicate that a detected subproject should not be treated as such")
            .withRequiredArg().ofType(String.class).defaultsTo(".gitversion.ignore");

        // ignore dir
        var ignoreDir0 = parser.accepts("ignore",
            "The directory name(s) to always ignore from counting as a subproject")
            .withOptionalArg().ofType(String.class);

        // working dir
        var working0 = parser.accepts("working",
            "Working directory to use")
            .withRequiredArg().ofType(File.class).defaultsTo(new File("."));

        // git root
        var gitRoot0 = parser.accepts("gitdir",
            "Forces the git directory to be the specified directory")
            .withOptionalArg().ofType(File.class);
        //@formatter:on

        var options = tryParse(parser, args);
        if (options == null || options.has(help0)) {
            parser.printHelpOn(System.out);
            return;
        }
    }

    private static @Nullable OptionSet tryParse(OptionParser parser, String[] args) {
        try {
            return parser.parse(args);
        } catch (OptionException e) {
            e.printStackTrace(System.out);
            return null;
        }
    }
}
