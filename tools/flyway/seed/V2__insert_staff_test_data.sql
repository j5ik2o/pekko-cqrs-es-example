-- これはテストデータで後で削除する。本来は dynamodb から rmu が insert する

-- テストデータ: 日本人スタッフ
INSERT INTO staff (
    id,
    staff_no,
    global_family_name,
    global_middle_name,
    global_given_name,
    local_family_name,
    local_middle_name,
    local_given_name,
    name_locale
) VALUES (
    '01J79Z8X000000000000000001',
    'STF001',
    'Yamada',
    NULL,
    'Taro',
    '山田',
    NULL,
    '太郎',
    'ja_JP'
);

-- テストデータ: アメリカ人スタッフ
INSERT INTO staff (
    id,
    staff_no,
    global_family_name,
    global_middle_name,
    global_given_name,
    local_family_name,
    local_middle_name,
    local_given_name,
    name_locale
) VALUES (
    '01J79Z8X000000000000000002',
    'STF002',
    'Smith',
    'James',
    'John',
    'Smith',
    'James',
    'John',
    'en_US'
);

-- テストデータ: 中国人スタッフ
INSERT INTO staff (
    id,
    staff_no,
    global_family_name,
    global_middle_name,
    global_given_name,
    local_family_name,
    local_middle_name,
    local_given_name,
    name_locale
) VALUES (
    '01J79Z8X000000000000000003',
    'STF003',
    'Wang',
    NULL,
    'Wei',
    '王',
    NULL,
    '偉',
    'zh_CN'
);
