# -*- mode: Python -*-
# Local dev loop:  tilt up   (UI at http://localhost:10350)
#
# For each Spring service:
#   1. "<svc>-compile" runs Maven on the host and extracts the boot jar into
#      <svc>/target/dev (rsync --checksum only touches files that changed).
#   2. docker_build builds the image from that output (Dockerfile stage "local").
#   3. On later code changes, live_update syncs the changed classes/jars into
#      the running container and restarts it - no image rebuild.

docker_compose('docker-compose.yml')

SPRING_SERVICES = ['api-gateway', 'user-service', 'order-service']

for svc in SPRING_SERVICES:
    out = '%s/target/dev' % svc
    local_resource(
        '%s-compile' % svc,
        cmd=' && '.join([
            './mvnw -q -B -o -pl %s -am package -DskipTests || ./mvnw -q -B -pl %s -am package -DskipTests' % (svc, svc),
            'rm -rf %s/target/staging' % svc,
            'java -Djarmode=tools -jar %s/target/%s.jar extract --launcher --destination %s/target/staging' % (svc, svc, svc),
            'mkdir -p %s' % out,
            'rsync -rc --delete %s/target/staging/ %s/' % (svc, out),
        ]),
        deps=['pom.xml', '%s/pom.xml' % svc, '%s/src/main' % svc],
        labels=['backend'],
    )

    docker_build(
        'devops-setup/%s' % svc,
        context='.',
        dockerfile='%s/Dockerfile' % svc,
        build_args={'APP_SOURCE': 'local'},
        only=[out],
        live_update=[
            sync(out, '/app'),
            restart_container(),
        ],
    )

    dc_resource(svc, resource_deps=['%s-compile' % svc], labels=['backend'])

dc_resource('postgres', labels=['db'])
dc_resource('redis', labels=['db'])

# Angular dev server (talks to the gateway on :8080)
local_resource(
    'frontend-install',
    cmd='npm ci',
    dir='frontend',
    deps=['frontend/package.json', 'frontend/package-lock.json'],
    labels=['frontend'],
)
local_resource(
    'frontend',
    serve_cmd='npm start',
    serve_dir='frontend',
    resource_deps=['frontend-install', 'api-gateway'],
    links=['http://localhost:4200'],
    labels=['frontend'],
)
