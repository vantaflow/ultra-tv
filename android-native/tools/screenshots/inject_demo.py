import sqlite3, subprocess, os
S=os.path.dirname(os.path.abspath(__file__)); PK='com.ultratv.tv.nativeapp.debug'
def sh(*a): return subprocess.run(a, capture_output=True)
sh('adb','shell','am','force-stop',PK)
for f in ['ultra-tv.db','ultra-tv.db-wal','ultra-tv.db-shm']:
    open(f'{S}/{f}','wb').write(sh('adb','exec-out','run-as',PK,'cat','databases/'+f).stdout)
c=sqlite3.connect(S+'/ultra-tv.db'); c.execute('delete from provider')
vals={'name':'Xtream','kind':'XTREAM','baseUrl':'http://10.0.2.2:8197','username':'demo','password':'demo','active':1,'lastLiveSyncAt':0,'lastVodSyncAt':0,'lastSeriesSyncAt':0,'lastEpgSyncAt':0,'categoryFilter':-1}
c.execute('insert into provider(%s) values(%s)'%(','.join(vals),','.join('?'*len(vals))),list(vals.values()))
c.commit(); c.execute('pragma wal_checkpoint(truncate)'); c.close()
sh('adb','shell','run-as',PK,'rm','-f','databases/ultra-tv.db-wal','databases/ultra-tv.db-shm')
sh('adb','push',S+'/ultra-tv.db','/data/local/tmp/ultra-tv.db')
sh('adb','shell','run-as',PK,'cp','/data/local/tmp/ultra-tv.db','databases/ultra-tv.db')
sh('adb','shell','rm','/data/local/tmp/ultra-tv.db')
for f in ['ultra-tv.db','ultra-tv.db-wal','ultra-tv.db-shm']:
    try: os.remove(f'{S}/{f}')
    except: pass
