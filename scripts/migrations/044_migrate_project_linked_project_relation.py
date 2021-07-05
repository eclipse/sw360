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
# This script to update linkedProjects field in project document to a new structure.
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

# get all the projects with linkedProjects
projects_all_query = {"selector": {"type": {"$eq": "project"}, "linkedProjects": {"$exists": True, "$ne": {}}}, "limit": 200000}

# ---------------------------------------
# functions
# ---------------------------------------

def run():
    log = {}
    log['updatedProjects'] = []
    print 'Getting all projects with linkedProjects'
    projects_all = db.find(projects_all_query)
    print 'Received ' + str(len(projects_all)) + ' projects'
    log['totalCount'] = len(projects_all)

    for project in projects_all:
        updateflag=False
        oldLinkedProjectsStructure = project.get('linkedProjects');
        for key, value in oldLinkedProjectsStructure.items():
            if type(value) == dict:
               continue
            projectProjectRelationship = { "projectRelationship" : value }
            oldLinkedProjectsStructure[key] = projectProjectRelationship
            updateflag = True
        if not updateflag:
            print 'Document already having new structure project ID -> ' + project.get('_id') + ', Project Name -> ' + project.get('name')
            continue
        print '\tUpdating project ID -> ' + project.get('_id') + ', Project Name -> ' + project.get('name')
        updatedProject = {}
        updatedProject['id'] = project.get('_id')
        updatedProject['name'] = project.get('name')
        log['updatedProjects'].append(updatedProject)
        if not DRY_RUN:
            db.save(project)
    resultFile = open('044_migrate_project_linked_project_relation.log', 'w')
    json.dump(log, resultFile, indent = 4, sort_keys = True)
    resultFile.close()

    print '\n'
    print '------------------------------------------'
    print 'Total Projects with linkedProjects'
    print '------------------------------------------'
    print 'Please check log file "044_migrate_project_linked_project_relation.log" in this directory for details'
    print '------------------------------------------'

# --------------------------------

startTime = time.time()
run()
print '\nTime of migration: ' + "{0:.2f}".format(time.time() - startTime) + 's'
