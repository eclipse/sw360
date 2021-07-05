#!/usr/bin/python
# -----------------------------------------------------------------------------
# Copyright Siemens AG, 2020. Part of the SW360 Portal Project.
#
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# This is a manual database migration script. It is assumed that a
# dedicated framework for automatic migration will be written in the
# future. When that happens, this script should be refactored to conform
# to the framework's prerequisites to be run by the framework. For
# example, server address and db name should be parameterized, the code
# reorganized into a single class or function, etc.
#
# This script to update linkedProjects field in project moderation request document to a new structure.
# -----------------------------------------------------------------------------

import time
import couchdb
import json
from webbrowser import get

# ---------------------------------------
# constants
# ---------------------------------------

DRY_RUN = True

COUCHSERVER = "http://localhost:5984/"
DBNAME = 'sw360db'

couch = couchdb.Server(COUCHSERVER)
db = couch[DBNAME]

# ----------------------------------------
# queries
# ----------------------------------------

# get all the project moderation request with linkedProjects changes
moderations_all_query = {"selector": {"type": {"$eq": "moderation"},"documentType": {"$eq": "PROJECT"},"$or": [{"projectAdditions": {"linkedProjects":{"$exists":True, "$ne": {}}}},{"projectDeletions": {"linkedProjects": {"$exists": True, "$ne": {}}}}]},"limit": 900000}

# ---------------------------------------
# functions
# ---------------------------------------

def run():
    log = {}
    log['updatedModerations'] = []
    print 'Getting all project moderation request with linkedProjects changes'
    moderations_all = db.find(moderations_all_query)
    print 'Received ' + str(len(moderations_all)) + ' moderation requests'
    log['totalCount'] = len(moderations_all)

    for moderation in moderations_all:
        updateflag = False
        oldProjectAdditionsLinkedProjectsStructure = moderation.get('projectAdditions').get('linkedProjects');
        oldProjectDeletionsLinkedProjectsStructure = moderation.get('projectDeletions').get('linkedProjects');
        for key, value in oldProjectAdditionsLinkedProjectsStructure.items():
            if type(value) == dict:
               continue
            projectProjectRelationship = { "projectRelationship" : value }
            oldProjectAdditionsLinkedProjectsStructure[key] = projectProjectRelationship
            updateflag = True
        for key, value in oldProjectDeletionsLinkedProjectsStructure.items():
            if type(value) == dict:
               continue
            projectProjectRelationship = { "projectRelationship" : value }
            oldProjectDeletionsLinkedProjectsStructure[key] = projectProjectRelationship
            updateflag = True
        if not updateflag:
            print 'Document already having new structure ID -> ' + moderation.get('_id') + ', Project Name -> ' + moderation.get('documentName')
            continue
        print '\tUpdating moderation ID -> ' + moderation.get('_id') + ', Project Name -> ' + moderation.get('documentName')
        updatedModeration = {}
        updatedModeration['id'] = moderation.get('_id')
        updatedModeration['projectName'] = moderation.get('documentName')
        log['updatedModerations'].append(updatedModeration)
        if not DRY_RUN:
            db.save(moderation)
    resultFile = open('045_migrate_project_moderation_request_linked_project_relation.log', 'w')
    json.dump(log, resultFile, indent = 4, sort_keys = True)
    resultFile.close()

    print '\n'
    print '------------------------------------------'
    print 'Please check log file "045_migrate_project_moderation_request_linked_project_relation.log" in this directory for details'
    print '------------------------------------------'

# --------------------------------

startTime = time.time()
run()
print '\nTime of migration: ' + "{0:.2f}".format(time.time() - startTime) + 's'
