package common.cn.kafei.simukraft.citizen;

import common.cn.kafei.simukraft.config.ServerConfig;
import net.minecraft.util.RandomSource;

import java.util.UUID;

public final class CitizenProfileGenerator {
    private static final String[] FAMILY_NAMES = {
            "王", "李", "张", "刘", "陈", "杨", "黄", "赵", "周", "吴",
            "徐", "孙", "朱", "马", "胡", "郭", "林", "何", "高", "郑",
            "罗", "梁", "谢", "宋", "唐", "许", "韩", "冯", "邓", "曹",
            "彭", "曾", "萧", "田", "董", "袁", "潘", "于", "蒋", "蔡",
            "魏", "薛", "叶", "阎", "余", "杜", "夏", "钟", "汪", "任",
            "姜", "范", "方", "石", "姚", "谭", "廖", "邹", "熊", "金",
            "陆", "郝", "孔", "白", "崔", "康", "毛", "邱", "秦", "江",
            "史", "顾", "侯", "邵", "孟", "龙", "万", "段", "雷", "钱",
            "汤", "尹", "黎", "易", "常", "武", "乔", "贺", "赖", "龚",
            "文", "庞", "樊", "兰", "殷", "施", "陶", "洪", "翟", "安",
            "颜", "倪", "严", "牛", "温", "季", "俞", "章", "鲁", "葛",
            "伍", "韦", "申", "尤", "毕", "聂", "焦", "向", "柳", "邢",
            "岳", "齐", "梅", "莫", "庄", "辛", "管", "祝", "左", "涂",
            "谷", "祁", "时", "舒", "耿", "卜", "詹", "关", "苗", "凌",
            "费", "纪", "靳", "盛", "童", "欧", "甄", "项", "曲", "成",
            "游", "阳", "裴", "席", "卫", "屈", "鲍", "覃", "霍", "翁",
            "甘", "景", "柯", "阮", "桂", "闵", "欧阳", "诸葛", "上官", "司马",
            "东方", "皇甫", "慕容", "司徒", "端木", "公孙", "轩辕", "令狐", "钟离", "宇文",
            "长孙", "鲜于", "澹台", "淳于", "太叔", "申屠", "仲孙", "颛孙", "巫马", "公西", "悠然"
    };
    private static final String[] MALE_GIVEN_NAMES = {
            "叙白", "砚丞", "翊珩", "昭野", "淮序", "既明", "晏清", "昀朗", "叙深", "砚舟",
            "允墨", "景曜", "叙川", "淮之", "昭临", "砚知", "翊川", "既望", "晏桥", "昀野",
            "淮安", "砚书", "翊声", "景深", "允和", "既白", "晏声", "昭棠", "砚卿", "允笙",
            "翊乔", "叙珩", "淮珩", "昭珩", "砚珩", "允珩", "既珩", "晏珩", "昀珩", "叙朗",
            "云深", "星河", "墨染", "瑾瑜", "修远", "明轩", "浩然", "子墨", "云帆", "景行",
            "修竹", "明德", "致远", "子谦", "云逸", "修文", "明志", "志远", "子安", "云开",
            "修齐", "明远", "志诚", "子建", "云舒", "修身", "明理", "志强", "子瑜", "云锦",
            "修心", "明义", "志明", "子骞", "云翔", "修道", "明道", "志高", "云涛", "修德",
            "明法", "子期", "云海", "修业", "明礼", "志新", "子敬", "云峰", "修睦", "明仁",
            "志勇", "子昂", "云汉", "明智", "志华", "子真", "志坚", "子美", "云泽", "修雅",
            "明达", "子文", "云溪", "明哲", "志宏", "子健", "云霓", "修诚", "明诚", "子厚",
            "云衢", "明敬", "子方", "云翼", "景泰", "子龙", "云涛", "修静",
            "临风", "望山", "踏歌", "怀远", "嘉木", "嘉瑞", "嘉禾", "嘉辰", "嘉树", "远山",
            "承泽", "承志", "承远", "承光", "承风", "守恒", "守诚", "守正", "守志", "守真",
            "长风", "长歌", "长河", "长思", "长青", "存仁", "立信", "知行", "知远", "知止",
            "观澜", "怀瑾", "秉义", "慎思", "秉烛", "持节", "知节", "知白", "青峦", "正心",
            "致知", "格致",
            "志刚", "志成", "文涛", "文斌", "文杰", "明辉", "宏伟", "嘉豪", "俊辉", "建平",
            "嘉俊", "俊豪", "春林", "松涛", "仁杰", "绍远", "浩博", "嘉林", "俊文", "晓峰",
            "泽林", "泽文", "泽博", "书文", "书林", "书博", "书远", "晓明", "晓华", "晓东",
            "晓强", "晓辉", "晓波", "晓林", "朝阳", "晨曦", "国栋", "国辉", "国平", "小天"
    };
    private static final String[] FEMALE_GIVEN_NAMES = {
            "叙柔", "淮月", "景芊", "叙蘅", "淮蘅", "景蘅", "叙棠", "砚棠", "既棠", "晏棠",
            "昀棠", "叙月", "砚月", "既月", "晏月", "昀月", "既夏", "晏宁", "昀舒", "淮朗",
            "清欢", "若水", "子衿", "逸尘", "瑾年", "清扬", "清和", "清泉", "清韵", "清音",
            "清辉", "清霜", "清露", "清影", "清秋", "清寒", "清晓", "清夜", "清昼", "清波",
            "清风", "明慧", "云梦", "小天",
            "听雨", "听雪", "揽月", "寻芳", "流光", "碧落", "苍云", "飞雪",
            "佳慧", "佳颖", "佳琳", "雨涵", "思涵", "欣悦", "欣然", "若兰", "慧琳", "淑雅",
            "秀娟", "丽华", "玉洁", "思雨", "思琪", "思彤", "如月", "如玉", "晓燕", "晓红",
            "书涵"
    };
    private static final String[] ENGLISH_MALE_GIVEN_NAMES = {
            "Arthur", "Edward", "George", "Henry", "James", "William", "Charles", "Thomas",
            "Albert", "Alfred", "Frederick", "Harold", "Louis", "Oliver", "Richard", "Robert"
    };
    private static final String[] ENGLISH_FEMALE_GIVEN_NAMES = {
            "Alice", "Charlotte", "Elizabeth", "Victoria", "Mary", "Anne", "Eleanor", "Florence",
            "Beatrice", "Catherine", "Edith", "Grace", "Isabel", "Jane", "Margaret", "Rose"
    };
    private static final String[] ENGLISH_FAMILY_NAMES = {
            "Smith", "Jones", "Taylor", "Brown", "Williams", "Wilson", "Davies", "Evans",
            "Thomas", "Johnson", "Roberts", "Walker", "Wright", "Thompson", "White", "Hughes"
    };
    private static final int MALE_SKIN_COUNT = 64;
    private static final int FEMALE_SKIN_COUNT = 64;

    private CitizenProfileGenerator() {
    }

    public static void fillMissingProfile(CitizenData data, RandomSource random, long gameDay) {
        if (isMissingGender(data.gender())) {
            data.setGender(random.nextDouble() < 0.5D ? "male" : "female");
        }
        if (data.name().isBlank()) {
            data.setName(createName(data.gender(), random));
        }
        if (data.skinPath().isBlank()) {
            data.setSkinPath(createSkinPath(data.gender(), data.uuid()));
        }
        if (data.age() <= 0) {
            data.setAge(18 + random.nextInt(8)); // 18~25
        }
        if (data.lifespan() < 18) {
            data.setLifespan(70 + random.nextInt(31)); // 70~100
        }
        if (data.bornDay() <= 0L) {
            data.setBornDay(gameDay - data.age() * 365L);
        }
    }

    private static boolean isMissingGender(String gender) {
        return gender == null || gender.isBlank() || "unknown".equalsIgnoreCase(gender);
    }

    private static String createName(String gender, RandomSource random) {
        if (ServerConfig.npcNameStyle() == CitizenNameStyle.ENGLISH) {
            return createEnglishName(gender, random);
        }
        return createChineseName(gender, random);
    }

    /** createChineseName: 按原有中式姓氏加名的规则生成 NPC 名字。 */
    private static String createChineseName(String gender, RandomSource random) {
        String familyName = FAMILY_NAMES[random.nextInt(FAMILY_NAMES.length)];
        String[] givenNames = "female".equals(gender) ? FEMALE_GIVEN_NAMES : MALE_GIVEN_NAMES;
        return familyName + givenNames[random.nextInt(givenNames.length)];
    }

    /** createEnglishName: 按英式名在前、姓在后的规则生成 NPC 名字。 */
    private static String createEnglishName(String gender, RandomSource random) {
        String[] givenNames = "female".equals(gender) ? ENGLISH_FEMALE_GIVEN_NAMES : ENGLISH_MALE_GIVEN_NAMES;
        String givenName = givenNames[random.nextInt(givenNames.length)];
        String familyName = ENGLISH_FAMILY_NAMES[random.nextInt(ENGLISH_FAMILY_NAMES.length)];
        return givenName + " " + familyName;
    }

    private static String createSkinPath(String gender, UUID uuid) {
        int skinCount = "female".equals(gender) ? FEMALE_SKIN_COUNT : MALE_SKIN_COUNT;
        int index = Math.floorMod(uuid.hashCode(), skinCount);
        String prefix = "female".equals(gender) ? "custom_female_entity_" : "custom_male_entity_";
        String folder = "female".equals(gender) ? "female" : "male";
        return "simukraft:textures/entity/" + folder + "/" + prefix + index + ".png";
    }

    private static final java.util.Set<String> COMPOUND_SURNAMES = java.util.Set.of(
            "欧阳","诸葛","上官","司马","东方","皇甫","慕容","司徒","端木","公孙",
            "轩辕","令狐","钟离","宇文","长孙","鲜于","澹台","淳于","太叔","申屠",
            "仲孙","颛孙","巫马","公西"
    );

    public static void fillChildProfile(CitizenData data, RandomSource random, long gameDay) {
        if (isMissingGender(data.gender())) {
            data.setGender(random.nextDouble() < 0.5D ? "male" : "female");
        }
        data.setAge(1);
        data.setBornDay(gameDay);
        // 从皮肤库取（渲染器缩放体现年龄感）
        data.setSkinPath(createSkinPath(data.gender(), data.uuid()));
        if (data.lifespan() < 18) {
            data.setLifespan(70 + random.nextInt(31)); // 70~100
        }
    }

    public static void promoteToAdult(CitizenData data, RandomSource random) {
        // age 已是18（tickGrowth 累加到18后调用），只换皮肤；寿命不足时补设
        data.setSkinPath(createSkinPath(data.gender(), data.uuid()));
        if (data.lifespan() <= 20) {
            data.setLifespan(70 + random.nextInt(31)); // 70~100
        }
    }

    public static String createChildName(String husbandName, String wifeName, String childGender, RandomSource random) {
        String familyName = extractFamilyName(husbandName);
        if (familyName.isEmpty()) {
            familyName = extractFamilyName(wifeName);
        }
        if (familyName.isEmpty()) {
            familyName = FAMILY_NAMES[random.nextInt(FAMILY_NAMES.length)];
        }
        if (ServerConfig.npcNameStyle() == CitizenNameStyle.ENGLISH) {
            String[] givenNames = "female".equals(childGender) ? ENGLISH_FEMALE_GIVEN_NAMES : ENGLISH_MALE_GIVEN_NAMES;
            return givenNames[random.nextInt(givenNames.length)] + " " + familyName;
        }
        String[] givenNames = "female".equals(childGender) ? FEMALE_GIVEN_NAMES : MALE_GIVEN_NAMES;
        return familyName + givenNames[random.nextInt(givenNames.length)];
    }

    public static String extractFamilyName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        // English: last token is family name
        if (fullName.contains(" ")) {
            String[] parts = fullName.trim().split("\\s+");
            return parts[parts.length - 1];
        }
        // Chinese: check compound surnames first
        if (fullName.length() >= 2) {
            String prefix2 = fullName.substring(0, 2);
            if (COMPOUND_SURNAMES.contains(prefix2)) return prefix2;
        }
        return fullName.substring(0, 1);
    }
}
