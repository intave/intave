// Updated IntavePlugin.java with removed duplicates and null check

package de.jpx3.intave;

public class IntavePlugin {
    // ... other code ...

    private void reloadConfiguration() {
        if (cloud != null) {
            cloud.configInit();
        }
    }

    // Other methods and attributes
    // ... additional existing code ...
}