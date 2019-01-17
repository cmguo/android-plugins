package dx.android.plugin;

public class PluginException extends Exception {

    private static final long serialVersionUID = 5132201477037789367L;
    
    private Plugin mPlugin;
    
    PluginException(Plugin plugin, String msg) {
        super(msg);
        mPlugin = plugin;
    }
    
    public Plugin getPlugin() {
        return mPlugin;
    }

}
