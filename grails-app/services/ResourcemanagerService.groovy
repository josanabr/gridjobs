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
       // http://graemerocher.blogspot.com/2008/10/new-gorm-features-coming-in-11.html
       def rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
       if ( (rc.numnodes - (rc.dead + rc.inuse)) > 0) {
          rc.inuse++
          // http://grails.org/doc/1.0.x/guide/single.html#5.3.5 Pessimistic and Optimistic Locking
          if (rc.save(flush: true) == null) {
             println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Error updating the 'rc' record"
             rc.errors.allErrors.each {
                println "[ResourceManagerService - allocatenode] \t ${it}"
             }
             return -1
          }
          return 0
       } else  {
          rc.save()
       }
       return -1
    }
    int allocatenode(String server) {
       def gr = Gridresource.findByName(server)
       def rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
       if ( (rc.numnodes - (rc.dead + rc.inuse)) > 0) {
          rc.inuse++
          // http://grails.org/doc/1.0.x/guide/single.html#5.3.5 Pessimistic and Optimistic Locking
          if (rc.save(flush: true) == null) {
             println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Error updating the 'rc' record"
             rc.errors.allErrors.each {
                println "[ResourceManagerService - allocatenode] \t ${it}"
             }
             return -1
          }
          return 0
       } else  {
          rc.save()
       }
       return -1
    }
    // -----------------

    // returns '0' on success, otherwise -1
    int releasenode(Gridresource gr) {
       def rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
       rc.inuse--
       if (rc.save(flush: true) == null) {
          println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Error updating the 'rc' record"
          rc.errors.allErrors.each {
             println "[ResourceManagerService - allocatenode] \t ${it}"
          }
          return -1
       } else {
          rc.save()
       }
       return 0
    }
    int releasenode(String server) {
       def gr = Gridresource.findByName(server)
       def rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
       rc.inuse--
       if (rc.save(flush: true) == null) {
          println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Error updating the 'rc' record"
          rc.errors.allErrors.each {
             println "[ResourceManagerService - allocatenode] \t ${it}"
          }
          return -1
       } else {
          rc.save()
       }
       return 0
    }
    // -----------------
    Resourcecharacteristics accessrc(Gridresource gr, boolean getting, Resourcecharacteristics rs) {
       Resourcecharacteristics rc = null
       rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
       if (getting) {
          return rc
       }
       rc.gridresource = rs.gridresource
       rc.numnodes = rs.numnodes
       rc.cpupernode = rs.cpupernode
       rc.dead = rs.dead
       rc.inuse = rs.inuse
       rc.cpuspeed = rs.cpuspeed
       rc.lastmodified = rs.lastmodified

       if (rc.save(flush: true) == null) {
          println "[ResourceManagerService - accessrc (${util.joda.Util.datetime()})] Error updating the 'rc' record"
          rc.errors.allErrors.each {
             println "[ResourceManagerService - accessrc] \t ${it}"
          }
          return null
       }

       return null
    }

}
