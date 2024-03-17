# Cheat Sheet

### BLOCK ACCESS

- Requires a user
- Super fast and threadsafe
- Supports ghostblocks, custom blockshapes, custom type translations
- Might have problems when accessing blocks far away from the player (!)
```
VolatileBlockAccess.typeAccess
VolatileBlockAccess.variantIndexAccess
VolatileBlockAccess.fluidAccess
VolatileBlockAccess.collisionShapeAccess
Fluids.fluidAt()
Fluids.fluitPresentAt()
```


### TRANSACTIONS

- Requires a user
- Should only be performed on the main thread

```
user.tickFeedback(() -> <callback>)
```

// Feel free to add more sections here yourself!
