class ResourcemanagerService {

    boolean transactional = true

    int totalnodes(Gridresource gr) {
       def rc = Resourcecharacteristics.findByGridresource(gr)
       return rc.numnodes
    }
    int totalnodes(String server) {
       def gr = Gridresource.findByName(server)
       return totalnodes(gr)
    }
    // -----------------

    int freenode(Gridresource gr) {
       def rc = Resourcecharacteristics.findByGridresource(gr)
       return rc.numnodes - (rc.dead + rc.inuse)
    }
    int freenode(String server) {
       def gr = Gridresource.findByName(server)
       return freenode(gr)
    }
    // -----------------

    int busynode(Gridresource gr) {
       def rc = Resourcecharacteristics.findByGridresource(gr)
       return rc.inuse
    }
    int busynode(String server) {
       def gr = Gridresource.findByName(server)
       return busynode(gr)
    }
    // -----------------

    int deadnode(Gridresource gr) {
       def rc = Resourcecharacteristics.findByGridresource(gr)
       return rc.dead
    }
    int deadnode(String server) {
       def gr = Gridresource.findByName(server)
       return deadnode(gr)
    }
    // -----------------

    int availablenodes(Gridresource gr) {
       return freenode(gr)
    }
    int availablenodes(String server) {
       return freenode(server)
    }
    // -----------------

    // It returns 0 if the resource could be allocated, 
    // otherwise -1
    int allocatenode(Gridresource gr) {
       def rc = Resourcecharacteristics.findByGridresource(gr)
       if (availablenodes(gr) > 0) {
          rc.inuse++
          if (rc.save() == null) {
             println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Error updating the 'rc' record"
             rc.errors.allErrors.each {
                println "[ResourceManagerService - allocatenode] \t ${it}"
             }
             return -1
          }
          return 0
       }
       return -1
    }
    int allocatenode(String server) {
       def gr = Gridresource.findByName(server)
       return allocatenode(gr)
    }
    // -----------------

    // returns '0' on success, otherwise -1
    int releasenode(Gridresource gr) {
       def rc = Resourcecharacteristics.findByGridresource(gr)
       rc.inuse--
       if (rc.save() == null) {
          println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Error updating the 'rc' record"
          rc.errors.allErrors.each {
             println "[ResourceManagerService - allocatenode] \t ${it}"
          }
          return -1
       }
       return 0
    }
    int releasenode(String server) {
       def gr = Gridresource.findByName(server)
       return releasenode(gr)
    }
    // -----------------

}
