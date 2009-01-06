class ResourcemanagerService {

    boolean transactional = true
    int low = 100
    int high = 1000
    int maxtries = 10

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
       def flag = true
       def counter = 0
       // http://graemerocher.blogspot.com/2008/10/new-gorm-features-coming-in-11.html
       while (flag) {
          flag = false
             // http://grails.org/doc/1.0.x/guide/single.html#5.3.5 Pessimistic and Optimistic Locking
          try { 
             def rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
             if ( (rc.numnodes - (rc.dead + rc.inuse)) > 0) {
                rc.inuse++
                if (rc.save(flush: true) == null) {
                   println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Error updating the 'rc' record"
                   rc.errors.allErrors.each {
                      println "[ResourceManagerService - allocatenode] \t ${it}"
                   }
                   return -1 // Error saving the record
                }
                println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] record updated!"
                return 0 // allocated and saved
             } else  {
                println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] No nodes available!"
                return 0 // no nodes available
             }
          } catch (Exception e) {
             println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Exception updating the 'rc' record"
             println "[ResourceManagerService - allocatenode] ${e}"
             flag = true
             def randomwaitingtime = util.Util.random(low,high)
             println "[ResourceManagerService - allocatenode] \t Waiting for ${randomwaitingtime} ms"
             Thread.sleep(randomwaitingtime)
             counter++
          }
          if (counter > maxtries) {
             println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Maximum number of tries reached"
             return -1 // maximum number of tries reached
          }
       }
       println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Exiting by default :-|"
       return 0 // by the flies :-D
    }
    int allocatenode(String server) {
       def gr = Gridresource.findByName(server)
       return allocatenode(gr)
       /*
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
       */
    }
    // -----------------

    // returns '0' on success, otherwise -1
    int releasenode(Gridresource gr) {
       def flag = true
       def counter = 0
       while (flag) {
          flag = false
          try { 
             def rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
             if (rc.inuse <= 0) {
                println "[ResourceManagerService - releasenode (${util.joda.Util.datetime()})] inuse less or equal to zero :-|"
                return -1
             }
             rc.inuse--
             if (rc.save(flush: true) == null) {
                println "[ResourceManagerService - releasenode (${util.joda.Util.datetime()})] Error updating the 'rc' record"
                rc.errors.allErrors.each {
                   println "[ResourceManagerService - releasenode] \t ${it}"
                }
                return -1
             } else {
                println "[ResourceManagerService - releasenode (${util.joda.Util.datetime()})] record updated!"
                return 0
             }
          } catch (Exception e) {
             println "[ResourceManagerService - releasenode (${util.joda.Util.datetime()})] Exception updating the 'rc' record"
             println "[ResourceManagerService - releasenode] ${e}"
             flag = true
             def randomwaitingtime = util.Util.random(low,high)
             println "[ResourceManagerService - releasenode] \t Waiting for ${randomwaitingtime} ms"
             Thread.sleep(randomwaitingtime)
             counter++
          }
          if (counter > maxtries) {
             println "[ResourceManagerService - releasenode] Maximum number of tries reached"
             return -1 // maximum number of tries reached
          }
       }
       return 0
    }
    int releasenode(String server) {
       def gr = Gridresource.findByName(server)
       return releasenode(gr)
       /*
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
       */
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
