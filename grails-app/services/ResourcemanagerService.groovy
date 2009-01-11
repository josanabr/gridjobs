class ResourcemanagerService {

    boolean transactional = true
    int low = 100
    int high = 1000
    int maxtries = 10
    def launchService

    int totalnodes(Gridresource gr) {
       def rc = Resourcecharacteristics.findByGridresource(gr)
       return rc.numnodes
    }
    int totalnodes(String server) {
       def gr = Gridresource.findByName(server)
       return totalnodes(gr)
    }
    // -----------------

    int freenode(Gridresource gr, int max = -1) {
       def rc = Resourcecharacteristics.findByGridresource(gr)
       //return rc.numnodes - (rc.dead + rc.inuse)
       def total = Task.findAll('from Task as t where (t.state = ? or t.state = ? or t.state = ?) and t.gridresource = ?',['UNSUBMITTED','PENDING','ACTIVE',gr]).size()
       if (max == -1) { 
          return rc.numnodes - total
       } else {
          return max - total
       }
    }
    int freenode(String server, int max = -1) {
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
    // 'max' is an optional parameter. When the user
    // assigns a different value to 'max', it's used for compare
    // the available nodes with this value.
    // When the value is '-1', the number of available
    // nodes is compare with the real number of nodes.

    int availablenodes(Gridresource gr, int max = -1) {
       return freenode(gr)
    }
    int availablenodes(String server, int max = -1) {
       return freenode(server)
    }
    // -----------------

    // It returns 0 if the resource could be allocated, 
    // otherwise -1
    int allocatenode(Gridresource gr) {
       return 0
       /*
       def flag = true
       def counter = 0
       // http://graemerocher.blogspot.com/2008/10/new-gorm-features-coming-in-11.html
       while (flag) {
          flag = false
             // http://grails.org/doc/1.0.x/guide/single.html#5.3.5 Pessimistic and Optimistic Locking
          def  rc = null
          try { 
             rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
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
             rc = null
             counter++
          }
          if (counter > maxtries) {
             println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Maximum number of tries reached"
             return -1 // maximum number of tries reached
          }
          if (flag) {
             def randomwaitingtime = util.Util.random(low,high)
             println "[ResourceManagerService - allocatenode] \t Waiting for ${randomwaitingtime} ms"
             Thread.sleep(randomwaitingtime)
          }
       }
       println "[ResourceManagerService - allocatenode (${util.joda.Util.datetime()})] Exiting by default :-|"
       return 0 // by the flies :-D
       */
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
       def rrstoreenable = Schedulerstatus.findBySequence(2)
       if (rrstoreenable.currentresource == 1) { // It's necessary to check for pending tasks
          def numpendingtask = Pendingtask.findAll('from pendingtask as p where p.attended = ?',[false]).size()
          if (numpendingtask <= 0) { // No pending tasks
             return 0
          }
          // Getting access to 'Pendingtask' table
          println "[ResourcemanagerService - releasenode] Looking for pending tasks"
          def locksuffix = 'rrstoresched'
          def dirname = "."
          def low = 500
          def high = 1000
          def maxtries = 10
          def counter = 1
          while (util.Util.lockexists(dirname,locksuffix)) {
             if (counter > maxtries) {
                return 0
             }
             counter++
             def waitingtime = util.Util.random(low,high)
             println "[ResourcemanagerService - releasenode] Trying to access to 'Pendingtask' table, waiting ${waitingtime} ms"
             Thread.sleep(waitingtime)
          }
          // Access 'granted'
          util.Util.createlock(dirname,locksuffix)
          //def pt  = Pendingtask.list(max:1,sort:'initialtime').get(0)
          def pt  = Pendingtask.find('from pendingtask as p where p.attended = ? order by p.initialtime', [false])
          if (pt == null) {
             println "[ResourcemanagerService - releasenode] 'Pendingtask' not available"
             return 0
          }
          pt.resumedtime = new DateTime().toDate()
          pt.attended = true
          pt.save()
          util.Util.deletelock(dirname,locksuffix)
          println "[ResourcemanagerService - releasenode] 'Pendingtask' was got, and dispatched to '${gr.name}'"
          launchService.execute(gr.name,pt.applicationname,pt.parameters,pt.executablename)
       }
       return 0
       /*
       def flag = true
       def counter = 0
       while (flag) {
          flag = false
          def rc = null 
          try { 
             rc = Resourcecharacteristics.findByGridresource(gr, [lock: true])
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
             rc = null
             counter++
          }
          if (counter > maxtries) {
             println "[ResourceManagerService - releasenode] Maximum number of tries reached"
             return -1 // maximum number of tries reached
          }
          if (flag) {
             def randomwaitingtime = util.Util.random(low,high)
             println "[ResourceManagerService - releasenode] \t Waiting for ${randomwaitingtime} ms"
             Thread.sleep(randomwaitingtime)
          }
       }
       return 0
       */
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
