module net.minecraftforge.gitver {
    exports net.minecraftforge.gitver;
    exports net.minecraftforge.gitver.changelog;

    requires net.minecraftforge.utils.git;
    requires net.minecraftforge.unsafe;
    requires org.apache.commons.io;
    requires org.eclipse.jgit;

    requires static org.jetbrains.annotations;
    requires joptsimple;
}