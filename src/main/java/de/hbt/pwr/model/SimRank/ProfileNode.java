package de.hbt.pwr.model.SimRank;


import com.fasterxml.jackson.annotation.JsonIgnore;
import de.hbt.pwr.model.profile.Profile;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
public class ProfileNode {

    @Getter
    @Setter
    private Long profileId;

    @Getter
    @Setter
    @JsonIgnore
    private Set<SkillNode> skillNodes = new HashSet<>();

    public ProfileNode(Profile profile) {
        this.profileId = profile.getId();
    }

    public void addSkillNode(SkillNode skillNode) {
        skillNodes.add(skillNode);
        skillNode.addProfileNode(this);
    }

    public int outgoing() {
        return skillNodes.size();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ProfileNode that = (ProfileNode) o;

        return profileId != null ? profileId.equals(that.profileId) : that.profileId == null;
    }

    @Override
    public int hashCode() {
        return profileId != null ? profileId.hashCode() : 0;
    }
}
