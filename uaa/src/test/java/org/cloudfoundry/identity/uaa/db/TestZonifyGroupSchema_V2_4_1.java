/*
 * *****************************************************************************
 *      Cloud Foundry
 *      Copyright (c) [2009-2015] Pivotal Software, Inc. All Rights Reserved.
 *      This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *      You may not use this product except in compliance with the License.
 *
 *      This product includes a number of subcomponents with
 *      separate copyright notices and license terms. Your use of these
 *      subcomponents is subject to the terms and conditions of the
 *      subcomponent's license, as noted in the LICENSE file.
 * *****************************************************************************
 */

package org.cloudfoundry.identity.uaa.db;

import org.cloudfoundry.identity.uaa.mock.InjectedMockContextTest;
import org.cloudfoundry.identity.uaa.scim.ScimGroup;
import org.cloudfoundry.identity.uaa.scim.ScimGroupMember;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.scim.endpoints.ScimGroupEndpoints;
import org.cloudfoundry.identity.uaa.scim.endpoints.ScimUserEndpoints;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneEndpoints;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.cloudfoundry.identity.uaa.zone.MultitenancyFixture;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TestZonifyGroupSchema_V2_4_1 extends InjectedMockContextTest {

    public static final int ENTITY_COUNT = 25;

    @Before
    public void populateDataUsingEndpoints() {

        RandomValueStringGenerator generator = new RandomValueStringGenerator(16);

        List<IdentityZone> zones = new LinkedList<>(Arrays.asList(IdentityZone.getUaa()));
        List<ScimGroup> groups = new LinkedList<>();

        for (int i=0; i<ENTITY_COUNT; i++) {
            String subdomain = generator.generate();
            IdentityZone zone = MultitenancyFixture.identityZone(subdomain, subdomain);
            getWebApplicationContext().getBean(IdentityZoneEndpoints.class).createIdentityZone(zone);
            zones.add(zone);
        }

        for (int i=0; i<ENTITY_COUNT; i++) {
            ScimGroup group = new ScimGroup(generator.generate());
            group = getWebApplicationContext().getBean(ScimGroupEndpoints.class).createGroup(group, new MockHttpServletResponse());
            groups.add(group);
        }

        Map<IdentityZone, List<ScimUser>> zoneUsers = new HashMap<>();
        for (IdentityZone zone : zones) {
            List<ScimUser> users = new LinkedList<>();
            for (int i=0; i<ENTITY_COUNT; i++) {
                String id = generator.generate();
                String email = id + "@test.org";
                ScimUser user = new ScimUser(null, id, id, id);
                user.setPrimaryEmail(email);
                user.setPassword(id);
                try {
                    IdentityZoneHolder.set(zone);
                    user = getWebApplicationContext().getBean(ScimUserEndpoints.class).createUser(user, new MockHttpServletResponse());
                    users.add(user);
                    ScimGroupMember member = new ScimGroupMember(user.getId());
                    ScimGroup group = getWebApplicationContext().getBean(ScimGroupEndpoints.class).getGroup(groups.get(i).getId(), new MockHttpServletResponse());
                    group.setMembers(Arrays.asList(member));
                    getWebApplicationContext().getBean(ScimGroupEndpoints.class).updateGroup(group, group.getId(),String.valueOf(group.getVersion()), new MockHttpServletResponse());
                }finally {
                    IdentityZoneHolder.clear();
                }

            }
            zoneUsers.put(zone, users);
        }


    }


    @Test
    public void test_Ensure_That_New_Fields_NotNull() {
        Assert.assertEquals(0, getWebApplicationContext().getBean(JdbcTemplate.class).queryForInt("SELECT count(*) FROM group_membership WHERE identity_zone_id IS NULL"));
        Assert.assertEquals(0, getWebApplicationContext().getBean(JdbcTemplate.class).queryForInt("SELECT count(*) FROM external_group_mapping WHERE identity_zone_id IS NULL"));
        Assert.assertEquals(0, getWebApplicationContext().getBean(JdbcTemplate.class).queryForInt("SELECT count(*) FROM external_group_mapping WHERE origin IS NULL"));
    }

}
