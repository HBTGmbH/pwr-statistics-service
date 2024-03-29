package de.hbt.pwr.service;

import de.hbt.pwr.StreamUtils;
import de.hbt.pwr.model.ConsultantSkillInfo;
import de.hbt.pwr.model.RelativeSkillUsage;
import de.hbt.pwr.model.SkillAverageRating;
import de.hbt.pwr.model.SkillUsage;
import de.hbt.pwr.model.clustering.ClusteredNetwork;
import de.hbt.pwr.model.clustering.ConsultantClusteringInfo;
import de.hbt.pwr.model.profile.*;
import de.hbt.pwr.model.profile.entries.ProfileEntry;
import de.hbt.pwr.repo.ClusteredNetworkRepo;
import de.hbt.pwr.repo.ConsultantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatisticsService {


    private final ConsultantRepository consultantRepository;

    private final ClusteredNetworkRepo clusteredNetworkRepo;


    /**
     * Defines a threshold(percentage) that a skill needs to achieve to qualify as 'common'
     */
    private static final float COMMON_SKILL_THRESHOLD = 0.8f;

    @Autowired
    public StatisticsService(ConsultantRepository consultantRepository, ClusteredNetworkRepo clusteredNetworkRepo) {
        this.consultantRepository = consultantRepository;
        this.clusteredNetworkRepo = clusteredNetworkRepo;
    }


    private static SkillUsage makeSkillUsage(Map.Entry<String, Integer> from) {
        return new SkillUsage(from.getKey(), from.getValue().longValue());
    }

    private List<SkillUsage> getSkillUsages() {
        final Map<String, Integer> usageBySkillName = new HashMap<>();
        StreamUtils.asStream(consultantRepository.findAll().iterator()).forEach(consultant -> consultant.getProfile().getSkills().forEach(skill -> {
            Integer usage = usageBySkillName.get(skill.getName());
            if (usage == null) {
                usage = 1;
            } else {
                usage += 1;
            }
            usageBySkillName.put(skill.getName(), usage);
        }));
        return usageBySkillName.entrySet().stream().map(StatisticsService::makeSkillUsage).collect(Collectors.toList());
    }

    private Set<String> getAllProfileSkillNames(String initials) {
        final Set<String> result = new HashSet<>();
        Optional<Consultant> consultant = StreamUtils.asStream(consultantRepository.findAll().iterator())
                .filter(consultant1 -> consultant1.getInitials().equals(initials)).findFirst();
        consultant.ifPresent(consultant1 -> consultant1.getProfile().getSkills().forEach(skill -> result.add(skill.getName())));
        return result;
    }

    public List<SkillUsage> getMostUsedSkills(int topResultCount) {
        List<SkillUsage> usageList = getSkillUsages();
        usageList.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));
        if (topResultCount < 0) {
            return usageList;
        }
        return usageList.subList(0, Math.min(usageList.size(), topResultCount));
    }

    public List<RelativeSkillUsage> getRelativeMostUsedSkills(int topResultCount) {
        List<SkillUsage> usageList = getMostUsedSkills(topResultCount);
        long profileCount = consultantRepository.count();
        return usageList.stream().map(skillUsage -> new RelativeSkillUsage(skillUsage, profileCount)).collect(Collectors.toList());
    }

    /**
     * Returns a list of skill-names that are interpreteable as common.
     *
     * @return list of common skill names
     */
    public List<String> getCommonSkills() {
        List<SkillUsage> usageList = getMostUsedSkills(-1);
        long profileCount = consultantRepository.count();
        usageList.removeIf(skillUsage -> !skillUsage.hasHigherUsage(COMMON_SKILL_THRESHOLD, profileCount));
        return usageList.stream().map(SkillUsage::getName).collect(Collectors.toList());
    }


    public void getCommonProfileSkills(List<String> foundCommonSkills, List<String> missingCommonSkills, String initials) {
        List<String> commonSkills = getCommonSkills();
        Set<String> profileSkills = getAllProfileSkillNames(initials);
        commonSkills.forEach(s -> {
            if (profileSkills.contains(s)) foundCommonSkills.add(s);
            else missingCommonSkills.add(s);
        });
    }

    public ClusteredNetwork getClusteredNetwork() {
        Optional<ClusteredNetwork> networkOptional = StreamUtils.asStream(clusteredNetworkRepo.findAll().iterator()).findFirst();
        return networkOptional.orElseThrow(() -> new RuntimeException("No network available."));
    }


    public ConsultantClusteringInfo getConsultantInfo(String initials) {
        ClusteredNetwork clusteredNetwork = clusteredNetworkRepo.findAll().iterator().next();
        if (clusteredNetwork == null) throw new RuntimeException("No clustered network available for analysis.");
        Optional<ClusteredNetwork.Node> nodeOptional = clusteredNetwork.getNodes().stream().filter(node -> node.initials.equals(initials)).findFirst();

        Map<String, Consultant> consultantsByInitials = new HashMap<>();
        StreamUtils.asStream(consultantRepository.findAll().iterator()).forEach(consultant -> consultantsByInitials.put(consultant.getInitials(), consultant));

        if (nodeOptional.isPresent()) {
            ClusteredNetwork.Node n = nodeOptional.get();
            ConsultantClusteringInfo info = new ConsultantClusteringInfo(consultantsByInitials.get(initials));
            info.setClusterId(n.cluster);
            // Get all cluster nodes
            List<ClusteredNetwork.Node> allNodes = clusteredNetwork.getClusterNodes(n.cluster);
            allNodes.forEach(aNode -> info.addNode(aNode, consultantsByInitials.get(aNode.initials).getProfile().getSkills()));
            info.evaluate();
            info.sort();
            return info;
        }
        return null;
    }


    public List<ConsultantSkillInfo> findConsultantsBySkills(List<String> skillNames) {
        List<Consultant> consultants = StreamUtils.asStream(consultantRepository.findAll()).collect(Collectors.toList());
        Set<String> skillNameSet = new HashSet<>(skillNames);
        consultants.removeIf(consultant -> !consultant.hasAllSkills(skillNameSet));
        return consultants.stream().map(consultant -> new ConsultantSkillInfo(consultant, skillNameSet)).collect(Collectors.toList());
    }

    private Set<Skill> getAllSkills(List<Consultant> consultants) {
        Set<Skill> skills = new HashSet<>();
        consultants.forEach(consultant -> skills.addAll(consultant.getProfile().getSkills()));
        return skills;
    }

    public List<SkillAverageRating> calculateRatedSkillFrequency() {
        Map<String, SkillAverageRating> ratedSkillsByName = new HashMap<>();
        List<Consultant> consultants = StreamUtils.asStream(consultantRepository.findAll()).collect(Collectors.toList());
        Set<Skill> skills = getAllSkills(consultants);
        skills.forEach(skill -> {
            SkillAverageRating s = ratedSkillsByName.get(skill.getName());
            if (s == null) {
                s = new SkillAverageRating(skill.getName());
                ratedSkillsByName.put(s.getName(), s);
            }
            s.addSkill(skill);
        });
        return ratedSkillsByName.values().stream().peek(SkillAverageRating::evaluate).collect(Collectors.toList());
    }

    public boolean hasNameEntity(Consultant consultant, String name, NameEntityType type) {
        Profile profile = consultant.getProfile();
        if (type == NameEntityType.COMPANY) {
            Set<NameEntity> companies = profile.getProjects().stream().map(Project::getBroker).collect(Collectors.toSet());
            companies.addAll(profile.getProjects().stream().map(Project::getClient).collect(Collectors.toSet()));
            return companies.stream()
                    .filter(Objects::nonNull)
                    .anyMatch(nameEntity -> Objects.equals(name, nameEntity.getName()));
        } else if (type == NameEntityType.PROJECT_ROLE) {
            final Set<NameEntity> roles = new HashSet<>();
            profile.getProjects().forEach(project -> roles.addAll(project.getProjectRoles()));
            return roles.stream().anyMatch(nameEntity -> Objects.equals(name, nameEntity.getName()));
        } else {
            Set<? extends ProfileEntry> lookup = new HashSet<>();
            switch (type) {
                case EDUCATION:
                    lookup = profile.getEducation();
                    break;
                case LANGUAGE:
                    lookup = profile.getLanguages();
                    break;
                case QUALIFICATION:
                    lookup = profile.getQualification();
                    break;
                case SECTOR:
                    lookup = profile.getSectors();
                    break;
                case TRAINING:
                    lookup = profile.getTrainingEntries();
                    break;
                case CAREER:
                    lookup = profile.getCareerEntries();
                    break;
                case KEY_SKILL:
                    lookup = profile.getKeySkillEntries();
                    break;
            }
            return lookup.stream().anyMatch(o -> o.getNameEntity() != null && name.equals(o.getNameEntity().getName()));
        }
    }

    public boolean hasSkill(Consultant consultant, String name) {
        return consultant.getProfile().getSkills().stream().anyMatch(skill -> name.equals(skill.getName()));
    }

    public List<Consultant> getAllConsultantsReferencingNameEntity(String nameEntityName, NameEntityType type) {
        return StreamUtils
                .asStream(consultantRepository.findAll())
                .filter(consultant -> hasNameEntity(consultant, nameEntityName, type))
                .collect(Collectors.toList());
    }

    public List<Consultant> getAllConsultantsReferencingSkill(String skillName) {
        return StreamUtils
                .asStream(consultantRepository.findAll())
                .filter(consultant -> hasSkill(consultant, skillName))
                .collect(Collectors.toList());

    }

}
