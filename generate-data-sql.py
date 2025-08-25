import json
import uuid


def load_json(file_path):
    with open(file_path, 'r') as file:
        return json.load(file)

def generate_uuid():
    return str(uuid.uuid4())

def map_user_ids(users):
    user_id_map = {}
    for user in users:
        user_id_map[user['id']] = generate_uuid()
    return user_id_map

def escape_sql_string(value):
    """Escape single quotes in SQL strings."""
    return value.replace("'", "''")

def generate_users_sql(users, user_id_map):
    sql_statements = []
    password_hash = "$2a$10$FZLU/ajV2PZmD9cXiZqNMuruo592lNW13uKcICG5oEBdnfYNbej1e"  # Use this hash for all passwords
    for user in users:
        new_id = user_id_map[user['id']]
        sql = f"INSERT INTO users (id, username, email, password, first_name, last_name, flat_number, street_address, city, postal_code, country, preferred_language, avatar_url, is_email_verified, disabled) VALUES ('{new_id}', '{escape_sql_string(user['username'])}', '{escape_sql_string(user['email'])}', '{password_hash}', '{escape_sql_string(user['firstName'])}', '{escape_sql_string(user['lastName'])}', '{escape_sql_string(user['flatNumber'])}', '{escape_sql_string(user['streetAddress'])}', '{escape_sql_string(user['city'])}', '{escape_sql_string(user['postalCode'])}', '{escape_sql_string(user['country'])}', '{escape_sql_string(user['preferredLanguage'])}', '{escape_sql_string(user.get('avatarUrl', ''))}', {str(user['isEmailVerified']).lower()}, {str(user['disabled']).lower()});"
        sql_statements.append(sql)
    return sql_statements

def generate_data_sql():
    users = load_json('platform-ui/src/app/mock/users.json')

    user_id_map = map_user_ids(users)

    sql_statements = []

    # Users
    sql_statements.append("-- Users")
    sql_statements.extend(generate_users_sql(users, user_id_map))
    sql_statements.append("")

    with open('db/postgres/data.sql', 'w') as file:
        for statement in sql_statements:
            file.write(statement + '\n')

if __name__ == "__main__":
    generate_data_sql()